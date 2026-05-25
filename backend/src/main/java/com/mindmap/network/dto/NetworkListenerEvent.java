package com.mindmap.network.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record NetworkListenerEvent(
        String id,
        OffsetDateTime capturedAt,
        String method,
        String path,
        String queryString,
        String sourceIp,
        String userAgent,
        String contentType,
        long contentLength,
        Map<String, String> headers,
        String bodyPreview,
        List<String> riskHints
) {
}
