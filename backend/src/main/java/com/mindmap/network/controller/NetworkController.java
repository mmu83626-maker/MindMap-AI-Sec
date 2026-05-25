package com.mindmap.network.controller;

import com.mindmap.agent.dto.AgentArtifact;
import com.mindmap.agent.service.AgentArtifactService;
import com.mindmap.network.dto.NetworkListenerEvent;
import com.mindmap.network.dto.NetworkMeasureReport;
import com.mindmap.network.dto.NetworkMeasureRequest;
import com.mindmap.network.dto.SecurityHeaderResult;
import com.mindmap.network.service.NetworkListenerService;
import com.mindmap.network.service.NetworkMeasureService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/network")
@CrossOrigin(origins = "*")
public class NetworkController {

    private final NetworkMeasureService networkMeasureService;
    private final NetworkListenerService networkListenerService;
    private final AgentArtifactService artifactService;

    public NetworkController(
            NetworkMeasureService networkMeasureService,
            NetworkListenerService networkListenerService,
            AgentArtifactService artifactService
    ) {
        this.networkMeasureService = networkMeasureService;
        this.networkListenerService = networkListenerService;
        this.artifactService = artifactService;
    }

    @PostMapping("/measure")
    public NetworkMeasureReport measure(@RequestBody NetworkMeasureRequest request) {
        return networkMeasureService.measure(request);
    }

    @GetMapping("/reports")
    public java.util.List<NetworkMeasureReport> reports(@RequestParam(defaultValue = "20") int limit) {
        return networkMeasureService.reports(limit);
    }

    @GetMapping("/reports/{id}")
    public NetworkMeasureReport report(@PathVariable String id) {
        return networkMeasureService.report(id);
    }

    @PostMapping("/reports/{id}/export")
    public AgentArtifact exportReport(
            @PathVariable String id,
            @RequestParam(defaultValue = "docx") String format
    ) {
        NetworkMeasureReport report = networkMeasureService.report(id);
        return artifactService.create(format, "network-report-" + report.host(), reportContent(report));
    }

    @PostMapping("/reports/compare/export")
    public AgentArtifact exportComparisonReport(
            @RequestParam String leftId,
            @RequestParam String rightId,
            @RequestParam(defaultValue = "docx") String format
    ) {
        NetworkMeasureReport left = networkMeasureService.report(leftId);
        NetworkMeasureReport right = networkMeasureService.report(rightId);
        return artifactService.create(format, "network-compare-" + left.host() + "-vs-" + right.host(), comparisonContent(left, right));
    }

    @GetMapping("/listener")
    public Map<String, Object> listenerStatus(HttpServletRequest request) {
        return networkListenerService.status(baseUrl(request));
    }

    @RequestMapping({"/listener/capture", "/listener/capture/**"})
    public NetworkListenerEvent capture(HttpServletRequest request, @RequestBody(required = false) String body) {
        return networkListenerService.capture(request, body);
    }

    @GetMapping("/listener/events")
    public List<NetworkListenerEvent> listenerEvents(@RequestParam(defaultValue = "50") int limit) {
        return networkListenerService.events(limit);
    }

    @DeleteMapping("/listener/events")
    public Map<String, Object> clearListenerEvents() {
        networkListenerService.clear();
        return Map.of("cleared", true, "timestamp", LocalDateTime.now().toString());
    }

    @PostMapping("/listener/export")
    public AgentArtifact exportListenerEvents(@RequestParam(defaultValue = "xlsx") String format) {
        return artifactService.create(format, "web-listener-events", listenerContent(networkListenerService.events(100)));
    }

    @PostMapping("/speedtest")
    public Map<String, Object> runSpeedTest() {
        return Map.of(
                "latency", Math.random() * 100,
                "downloadSpeed", Math.random() * 100,
                "uploadSpeed", Math.random() * 50,
                "jitter", Math.random() * 20,
                "timestamp", LocalDateTime.now()
        );
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "healthy",
                "timestamp", LocalDateTime.now().toString()
        );
    }

    private String baseUrl(HttpServletRequest request) {
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(request.getScheme()) && port == 80)
                || ("https".equals(request.getScheme()) && port == 443);
        return request.getScheme() + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
    }

    private String reportContent(NetworkMeasureReport report) {
        StringBuilder content = new StringBuilder();
        content.append("Network Security and Measurement Report\n");
        content.append("Section\tMetric\tValue\tAnalysis\n");
        content.append(row("Target", "URL", report.target(), report.finalUrl()));
        content.append(row("Target", "Host", report.host(), String.join(" / ", report.ipAddresses())));
        content.append(row("Security", "Risk level", report.riskLevel(), report.summary()));
        content.append(row("Security", "HTTPS", report.httpsEnabled() ? "enabled" : "disabled", report.httpsEnabled() ? "Transport is encrypted." : "Use HTTPS."));
        content.append(row("Security", "TLS certificate", report.certificateValid() ? "valid" : "invalid", certificateSummary(report)));
        content.append(row("Measurement", "DNS", ms(report.dnsMs()), "Domain resolution latency."));
        content.append(row("Measurement", "TCP", ms(report.tcpMs()), "Connection establishment latency."));
        content.append(row("Measurement", "TLS", ms(report.tlsMs()), "Handshake latency for HTTPS."));
        content.append(row("Measurement", "TTFB", ms(report.ttfbMs()), "Web application first byte latency."));
        content.append(row("Measurement", "Total", ms(report.totalMs()), "End-to-end request duration."));
        content.append(row("Measurement", "Response bytes", String.valueOf(report.responseBytes() == null ? 0 : report.responseBytes()), "Downloaded body size sample."));
        for (SecurityHeaderResult header : report.securityHeaders()) {
            content.append(row("Web Security Header", header.name(), header.present() ? "present" : "missing", header.present() ? header.value() : header.recommendation()));
        }
        if (report.risks().isEmpty()) {
            content.append(row("Risk", "Finding", "none", "No obvious HTTPS, TLS, or security-header issue was detected."));
        } else {
            for (String risk : report.risks()) {
                content.append(row("Risk", "Finding", risk, "Prioritize remediation based on exposure and exploitability."));
            }
        }
        return content.toString();
    }

    private String listenerContent(List<NetworkListenerEvent> events) {
        StringBuilder content = new StringBuilder();
        content.append("Web Application Listener Events\n");
        content.append("Captured At\tMethod\tPath\tQuery\tSource IP\tUser-Agent\tContent-Type\tBody Preview\tRisk Hints\n");
        for (NetworkListenerEvent event : events) {
            content.append(escape(event.capturedAt().toString())).append('\t')
                    .append(escape(event.method())).append('\t')
                    .append(escape(event.path())).append('\t')
                    .append(escape(event.queryString())).append('\t')
                    .append(escape(event.sourceIp())).append('\t')
                    .append(escape(event.userAgent())).append('\t')
                    .append(escape(event.contentType())).append('\t')
                    .append(escape(event.bodyPreview())).append('\t')
                    .append(escape(String.join("; ", event.riskHints()))).append('\n');
        }
        if (events.isEmpty()) {
            content.append("No events captured yet.\n");
        }
        return content.toString();
    }

    private String comparisonContent(NetworkMeasureReport left, NetworkMeasureReport right) {
        StringBuilder content = new StringBuilder();
        content.append("Network Benchmark Comparison Report\n");
        content.append("Metric\t").append(escape(left.host())).append('\t').append(escape(right.host())).append("\tAnalysis\n");
        content.append(compareRow("Score", String.valueOf(score(left)), String.valueOf(score(right)), score(left) >= score(right) ? left.host() + " has the stronger overall posture." : right.host() + " has the stronger overall posture."));
        content.append(compareRow("Risk level", left.riskLevel(), right.riskLevel(), "Lower risk level is better."));
        content.append(compareRow("HTTPS", left.httpsEnabled() ? "enabled" : "disabled", right.httpsEnabled() ? "enabled" : "disabled", "HTTPS protects transport confidentiality and integrity."));
        content.append(compareRow("TLS certificate", left.certificateValid() ? "valid" : "invalid", right.certificateValid() ? "valid" : "invalid", "Invalid or expiring certificates are high priority."));
        content.append(compareRow("DNS", ms(left.dnsMs()), ms(right.dnsMs()), faster(left.dnsMs(), right.dnsMs(), left.host(), right.host())));
        content.append(compareRow("TCP", ms(left.tcpMs()), ms(right.tcpMs()), faster(left.tcpMs(), right.tcpMs(), left.host(), right.host())));
        content.append(compareRow("TLS", ms(left.tlsMs()), ms(right.tlsMs()), faster(left.tlsMs(), right.tlsMs(), left.host(), right.host())));
        content.append(compareRow("TTFB", ms(left.ttfbMs()), ms(right.ttfbMs()), faster(left.ttfbMs(), right.ttfbMs(), left.host(), right.host())));
        content.append(compareRow("Total", ms(left.totalMs()), ms(right.totalMs()), faster(left.totalMs(), right.totalMs(), left.host(), right.host())));
        content.append(compareRow("Security headers", headerCoverage(left), headerCoverage(right), "Higher security-header coverage reduces common browser-side risks."));
        content.append("\nLeft findings\n");
        for (String risk : left.risks()) {
            content.append("- ").append(escape(risk)).append('\n');
        }
        content.append("\nRight findings\n");
        for (String risk : right.risks()) {
            content.append("- ").append(escape(risk)).append('\n');
        }
        return content.toString();
    }

    private String compareRow(String metric, String left, String right, String analysis) {
        return escape(metric) + '\t' + escape(left) + '\t' + escape(right) + '\t' + escape(analysis) + '\n';
    }

    private String faster(Long left, Long right, String leftHost, String rightHost) {
        if (left == null || right == null) {
            return "One side has no measurement.";
        }
        if (left.equals(right)) {
            return "Both sides are tied.";
        }
        return (left < right ? leftHost : rightHost) + " is faster for this stage.";
    }

    private String headerCoverage(NetworkMeasureReport report) {
        long present = report.securityHeaders().stream().filter(SecurityHeaderResult::present).count();
        return present + "/" + report.securityHeaders().size();
    }

    private int score(NetworkMeasureReport report) {
        int score = 100;
        if (!report.httpsEnabled()) score -= 20;
        if (!report.certificateValid()) score -= 20;
        if (report.certificateDaysRemaining() != null && report.certificateDaysRemaining() < 30) score -= 5;
        long missingImportant = report.securityHeaders().stream()
                .filter(item -> !item.present())
                .filter(item -> item.name().equals("Content-Security-Policy")
                        || item.name().equals("Strict-Transport-Security")
                        || item.name().equals("X-Frame-Options")
                        || item.name().equals("X-Content-Type-Options"))
                .count();
        score -= (int) missingImportant * 5;
        if (report.httpStatus() == null || report.httpStatus() >= 500) score -= 15;
        else if (report.httpStatus() >= 400) score -= 8;
        if (report.totalMs() != null && report.totalMs() > 5000) score -= 10;
        else if (report.totalMs() != null && report.totalMs() > 2000) score -= 5;
        return Math.max(0, score);
    }

    private String certificateSummary(NetworkMeasureReport report) {
        if (report.certificateDaysRemaining() == null) {
            return "Certificate metadata is unavailable.";
        }
        return "Subject: " + report.certificateSubject()
                + "; issuer: " + report.certificateIssuer()
                + "; days remaining: " + report.certificateDaysRemaining();
    }

    private String row(String section, String metric, String value, String analysis) {
        return escape(section) + '\t' + escape(metric) + '\t' + escape(value) + '\t' + escape(analysis) + '\n';
    }

    private String ms(Long value) {
        return value == null ? "n/a" : value + " ms";
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }
}
