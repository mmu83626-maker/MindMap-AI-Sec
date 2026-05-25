package com.mindmap.network.service;

import com.mindmap.network.dto.NetworkListenerEvent;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NetworkListenerService {

    private static final int MAX_BODY_PREVIEW = 4000;
    private final CopyOnWriteArrayList<NetworkListenerEvent> events = new CopyOnWriteArrayList<>();

    public NetworkListenerEvent capture(HttpServletRequest request, String body) {
        Map<String, String> headers = selectedHeaders(request);
        String query = request.getQueryString() == null ? "" : request.getQueryString();
        String preview = bodyPreview(body);
        NetworkListenerEvent event = new NetworkListenerEvent(
                UUID.randomUUID().toString(),
                OffsetDateTime.now(),
                request.getMethod(),
                request.getRequestURI(),
                query,
                sourceIp(request),
                request.getHeader("User-Agent") == null ? "" : request.getHeader("User-Agent"),
                request.getContentType() == null ? "" : request.getContentType(),
                Math.max(0L, request.getContentLengthLong()),
                headers,
                preview,
                riskHints(request, query, preview)
        );
        events.add(0, event);
        trimHistory();
        return event;
    }

    public List<NetworkListenerEvent> events(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return events.stream()
                .sorted(Comparator.comparing(NetworkListenerEvent::capturedAt).reversed())
                .limit(safeLimit)
                .toList();
    }

    public void clear() {
        events.clear();
    }

    public Map<String, Object> status(String publicBaseUrl) {
        return Map.of(
                "enabled", true,
                "capturePath", "/api/network/listener/capture",
                "captureUrl", publicBaseUrl + "/api/network/listener/capture",
                "totalEvents", events.size(),
                "lastCapturedAt", events.isEmpty() ? "" : events.get(0).capturedAt().toString(),
                "capabilities", List.of(
                        "GET/POST/PUT/DELETE request capture",
                        "Header and body preview",
                        "Web application request risk hints",
                        "Word and Excel export"
                )
        );
    }

    private Map<String, String> selectedHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        List<String> names = Collections.list(request.getHeaderNames());
        names.stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(name -> {
            String lower = name.toLowerCase(Locale.ROOT);
            String value = request.getHeader(name);
            if (lower.equals("authorization") || lower.equals("cookie") || lower.equals("set-cookie")) {
                value = value == null || value.isBlank() ? "" : "[redacted]";
            }
            headers.put(name, value == null ? "" : value);
        });
        return headers;
    }

    private String sourceIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp == null || realIp.isBlank() ? request.getRemoteAddr() : realIp.trim();
    }

    private String bodyPreview(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String normalized = body.replace("\u0000", "").trim();
        if (normalized.length() <= MAX_BODY_PREVIEW) {
            return normalized;
        }
        return normalized.substring(0, MAX_BODY_PREVIEW) + "...[truncated]";
    }

    private List<String> riskHints(HttpServletRequest request, String query, String bodyPreview) {
        List<String> hints = new ArrayList<>();
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            hints.add("Missing User-Agent; may be a script, scanner, or non-browser client.");
        }
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        if ((origin != null && !origin.isBlank()) || (referer != null && !referer.isBlank())) {
            hints.add("Browser-originated request detected; validate CORS and CSRF rules for state-changing routes.");
        }
        String contentType = request.getContentType();
        if (request.getContentLengthLong() > 0 && (contentType == null || contentType.isBlank())) {
            hints.add("Request has a body but no Content-Type.");
        }
        String combined = (query + "\n" + bodyPreview).toLowerCase(Locale.ROOT);
        if (combined.contains("<script") || combined.contains("javascript:")) {
            hints.add("Payload contains script-like content; check XSS handling.");
        }
        if (combined.contains("' or ") || combined.contains("\" or ") || combined.contains(" union select ")) {
            hints.add("Payload contains SQL-injection-like tokens.");
        }
        if (combined.contains("../") || combined.contains("..\\") || combined.contains("%2e%2e")) {
            hints.add("Payload contains path-traversal-like tokens.");
        }
        if (hints.isEmpty()) {
            hints.add("No obvious request-level risk pattern found.");
        }
        return List.copyOf(hints);
    }

    private void trimHistory() {
        while (events.size() > 300) {
            events.remove(events.size() - 1);
        }
    }
}
