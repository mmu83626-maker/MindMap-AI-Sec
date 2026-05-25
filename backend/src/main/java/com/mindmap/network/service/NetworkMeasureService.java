package com.mindmap.network.service;

import com.mindmap.network.dto.NetworkMeasureReport;
import com.mindmap.network.dto.NetworkMeasureRequest;
import com.mindmap.network.dto.SecurityHeaderResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NetworkMeasureService {

    private static final int CONNECT_TIMEOUT_MS = 6000;
    private static final int READ_TIMEOUT_MS = 12000;
    private static final int MAX_BODY_BYTES = 1024 * 1024;

    private final CopyOnWriteArrayList<NetworkMeasureReport> reports = new CopyOnWriteArrayList<>();

    public NetworkMeasureReport measure(NetworkMeasureRequest request) {
        URI uri = normalizeUri(request.url());
        long dnsStart = System.nanoTime();
        InetAddress[] addresses;
        Long dnsMs;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
            dnsMs = elapsedMs(dnsStart);
        } catch (Exception ex) {
            return failureReport(
                    uri,
                    elapsedMs(dnsStart),
                    "DNS resolution failed: " + ex.getMessage(),
                    "high"
            );
        }
        assertPublicTarget(uri, addresses);

        List<String> risks = new ArrayList<>();
        List<SecurityHeaderResult> headers = List.of();
        List<String> ipAddresses = List.of(addresses).stream().map(InetAddress::getHostAddress).toList();
        Long tcpMs = null;
        Long tlsMs = null;
        CertificateInfo certificateInfo = CertificateInfo.empty();
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        boolean tcpReachable = false;

        try {
            tcpMs = measureTcp(uri, addresses[0]);
            tcpReachable = true;
        } catch (ResponseStatusException ex) {
            risks.add(reason(ex, "TCP connection failed."));
        }

        if (https && tcpReachable) {
            try {
                TlsResult tls = measureTls(uri, addresses[0]);
                tlsMs = tls.durationMs();
                certificateInfo = tls.certificateInfo();
                if (!certificateInfo.valid()) {
                    risks.add("TLS certificate is not currently valid.");
                }
                if (certificateInfo.daysRemaining() != null && certificateInfo.daysRemaining() < 15) {
                    risks.add("TLS certificate expires within 15 days.");
                }
            } catch (ResponseStatusException ex) {
                risks.add(reason(ex, "TLS handshake failed."));
            }
        } else if (!https) {
            risks.add("Target is using plain HTTP instead of HTTPS.");
        }

        HttpResult http = measureHttp(uri);
        headers = evaluateHeaders(http.headers(), https);
        for (SecurityHeaderResult header : headers) {
            if (!header.present()) {
                risks.add("Missing security header: " + header.name());
            }
        }
        if (http.status() == null || http.status() >= 500) {
            risks.add("Web application is unavailable or returned a server error.");
        } else if (http.status() >= 400) {
            risks.add("Web application returned a client error status.");
        }

        String riskLevel = classifyRisk(risks, https, headers, certificateInfo);
        NetworkMeasureReport report = new NetworkMeasureReport(
                UUID.randomUUID().toString(),
                uri.toString(),
                http.finalUrl(),
                uri.getHost(),
                ipAddresses,
                OffsetDateTime.now(),
                http.status() != null && http.status() < 500,
                https,
                http.status(),
                http.statusText(),
                dnsMs,
                tcpMs,
                tlsMs,
                http.ttfbMs(),
                http.totalMs(),
                http.responseBytes(),
                certificateInfo.valid(),
                certificateInfo.subject(),
                certificateInfo.issuer(),
                certificateInfo.validFrom(),
                certificateInfo.validTo(),
                certificateInfo.daysRemaining(),
                headers,
                List.copyOf(risks),
                riskLevel,
                buildSummary(riskLevel, risks, dnsMs, tcpMs, tlsMs, http)
        );
        reports.add(0, report);
        trimHistory();
        return report;
    }

    public List<NetworkMeasureReport> reports(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return reports.stream()
                .sorted(Comparator.comparing(NetworkMeasureReport::checkedAt).reversed())
                .limit(safeLimit)
                .toList();
    }

    public NetworkMeasureReport report(String id) {
        return reports.stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Network report not found"));
    }

    private NetworkMeasureReport failureReport(URI uri, Long dnsMs, String reason, String riskLevel) {
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        List<SecurityHeaderResult> headers = evaluateHeaders(Map.of(), https);
        List<String> risks = new ArrayList<>();
        risks.add(reason);
        risks.add("Measurement stopped before TCP/TLS/HTTP because the target could not be resolved.");
        NetworkMeasureReport report = new NetworkMeasureReport(
                UUID.randomUUID().toString(),
                uri.toString(),
                uri.toString(),
                uri.getHost(),
                List.of(),
                OffsetDateTime.now(),
                false,
                https,
                null,
                reason,
                dnsMs,
                null,
                null,
                null,
                dnsMs,
                0L,
                false,
                "",
                "",
                null,
                null,
                null,
                headers,
                List.copyOf(risks),
                riskLevel,
                "Risk level: " + riskLevel + ". Main finding: " + reason
                        + " Measurement stopped at DNS, so TCP/TLS/TTFB are unavailable."
        );
        reports.add(0, report);
        trimHistory();
        return report;
    }

    private URI normalizeUri(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL is required");
        }
        String value = rawUrl.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://" + value;
        }
        try {
            URI uri = URI.create(value);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only http and https URLs are supported");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL host is required");
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URL", ex);
        }
    }

    private InetAddress[] resolveAddresses(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "DNS resolution failed: " + ex.getMessage(), ex);
        }
    }

    private void assertPublicTarget(URI uri, InetAddress[] addresses) {
        for (InetAddress address : addresses) {
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Private, local, and multicast targets are not allowed for network measurement"
                );
            }
        }
        if ("localhost".equalsIgnoreCase(uri.getHost())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "localhost is not allowed");
        }
    }

    private Long measureDns(String host) {
        long start = System.nanoTime();
        resolveAddresses(host);
        return elapsedMs(start);
    }

    private String reason(ResponseStatusException ex, String fallback) {
        return ex.getReason() == null || ex.getReason().isBlank() ? fallback : ex.getReason();
    }

    private Long measureTcp(URI uri, InetAddress address) {
        int port = port(uri);
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MS);
            return elapsedMs(start);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TCP connection failed: " + ex.getMessage(), ex);
        }
    }

    private TlsResult measureTls(URI uri, InetAddress address) {
        int port = port(uri);
        long start = System.nanoTime();
        try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket()) {
            socket.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            socket.startHandshake();
            Long duration = elapsedMs(start);
            CertificateInfo certificate = CertificateInfo.from(socket.getSession().getPeerCertificates());
            return new TlsResult(duration, certificate);
        } catch (SSLPeerUnverifiedException ex) {
            return new TlsResult(elapsedMs(start), CertificateInfo.empty());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TLS handshake failed: " + ex.getMessage(), ex);
        }
    }

    private HttpResult measureHttp(URI uri) {
        long start = System.nanoTime();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "NetScope-AI/1.0");
            long beforeStatus = System.nanoTime();
            int status = connection.getResponseCode();
            Long ttfb = elapsedMs(beforeStatus);
            long responseBytes = readLimited(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            Long total = elapsedMs(start);
            String finalUrl = connection.getURL() == null ? uri.toString() : connection.getURL().toString();
            return new HttpResult(status, connection.getResponseMessage(), finalUrl, ttfb, total, responseBytes, connection.getHeaderFields());
        } catch (Exception ex) {
            return new HttpResult(null, ex.getMessage(), uri.toString(), null, elapsedMs(start), 0L, Map.of());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private List<SecurityHeaderResult> evaluateHeaders(Map<String, List<String>> rawHeaders, boolean https) {
        return List.of(
                header(rawHeaders, "Content-Security-Policy", "Restricts script, style, frame, and resource origins.", "Add a CSP that allows only trusted sources."),
                header(rawHeaders, "Strict-Transport-Security", "Forces browsers to use HTTPS on future visits.", "Add HSTS with a reasonable max-age.", https),
                header(rawHeaders, "X-Frame-Options", "Reduces clickjacking by blocking unwanted framing.", "Use DENY or SAMEORIGIN unless framing is required."),
                header(rawHeaders, "X-Content-Type-Options", "Prevents MIME sniffing for script and style resources.", "Set X-Content-Type-Options: nosniff."),
                header(rawHeaders, "Referrer-Policy", "Controls how much referrer data is leaked to other sites.", "Set strict-origin-when-cross-origin or stricter."),
                header(rawHeaders, "Permissions-Policy", "Limits browser features such as camera, microphone, and geolocation.", "Disable unused browser capabilities.")
        );
    }

    private SecurityHeaderResult header(Map<String, List<String>> headers, String name, String meaning, String recommendation) {
        return header(headers, name, meaning, recommendation, true);
    }

    private SecurityHeaderResult header(Map<String, List<String>> headers, String name, String meaning, String recommendation, boolean expected) {
        String value = findHeader(headers, name);
        boolean present = value != null && !value.isBlank();
        return new SecurityHeaderResult(name, present || !expected, present ? value : "", meaning, expected ? recommendation : "Only required for HTTPS targets.");
    }

    private String findHeader(Map<String, List<String>> headers, String name) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return String.join(", ", entry.getValue());
            }
        }
        return null;
    }

    private String classifyRisk(List<String> risks, boolean https, List<SecurityHeaderResult> headers, CertificateInfo certificateInfo) {
        if (!https || !certificateInfo.valid()) {
            return "high";
        }
        long missingImportant = headers.stream()
                .filter(item -> !item.present())
                .filter(item -> item.name().equals("Content-Security-Policy")
                        || item.name().equals("Strict-Transport-Security")
                        || item.name().equals("X-Frame-Options"))
                .count();
        if (missingImportant >= 2 || risks.size() >= 4) {
            return "medium";
        }
        if (!risks.isEmpty()) {
            return "low";
        }
        return "good";
    }

    private String buildSummary(String riskLevel, List<String> risks, Long dnsMs, Long tcpMs, Long tlsMs, HttpResult http) {
        StringBuilder builder = new StringBuilder();
        builder.append("Risk level: ").append(riskLevel).append(". ");
        if (risks.isEmpty()) {
            builder.append("No major HTTPS, TLS, or security-header risks were found. ");
        } else {
            builder.append("Main findings: ").append(String.join("; ", risks.subList(0, Math.min(3, risks.size())))).append(". ");
        }
        builder.append("Measurement: DNS ").append(ms(dnsMs))
                .append(", TCP ").append(ms(tcpMs));
        if (tlsMs != null) {
            builder.append(", TLS ").append(ms(tlsMs));
        }
        builder.append(", TTFB ").append(ms(http.ttfbMs()))
                .append(", total ").append(ms(http.totalMs()))
                .append(".");
        return builder.toString();
    }

    private String ms(Long value) {
        return value == null ? "n/a" : value + " ms";
    }

    private long readLimited(InputStream stream) throws Exception {
        if (stream == null) {
            return 0;
        }
        long total = 0;
        byte[] buffer = new byte[8192];
        try (InputStream input = stream) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total >= MAX_BODY_BYTES) {
                    break;
                }
            }
        }
        return total;
    }

    private int port(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private Long elapsedMs(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    private void trimHistory() {
        while (reports.size() > 100) {
            reports.remove(reports.size() - 1);
        }
    }

    private record HttpResult(
            Integer status,
            String statusText,
            String finalUrl,
            Long ttfbMs,
            Long totalMs,
            Long responseBytes,
            Map<String, List<String>> headers
    ) {
    }

    private record TlsResult(Long durationMs, CertificateInfo certificateInfo) {
    }

    private record CertificateInfo(
            boolean valid,
            String subject,
            String issuer,
            OffsetDateTime validFrom,
            OffsetDateTime validTo,
            Long daysRemaining
    ) {
        static CertificateInfo empty() {
            return new CertificateInfo(false, "", "", null, null, null);
        }

        static CertificateInfo from(Certificate[] certificates) {
            if (certificates == null || certificates.length == 0 || !(certificates[0] instanceof X509Certificate cert)) {
                return empty();
            }
            OffsetDateTime from = cert.getNotBefore().toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
            OffsetDateTime to = cert.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
            OffsetDateTime now = OffsetDateTime.now();
            boolean valid = now.isAfter(from) && now.isBefore(to);
            long days = Duration.between(now, to).toDays();
            return new CertificateInfo(
                    valid,
                    cert.getSubjectX500Principal().getName(),
                    cert.getIssuerX500Principal().getName(),
                    from,
                    to,
                    days
            );
        }
    }
}

