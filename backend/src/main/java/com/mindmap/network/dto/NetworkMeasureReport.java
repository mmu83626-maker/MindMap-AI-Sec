package com.mindmap.network.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record NetworkMeasureReport(
        String id,
        String target,
        String finalUrl,
        String host,
        List<String> ipAddresses,
        OffsetDateTime checkedAt,
        boolean reachable,
        boolean httpsEnabled,
        Integer httpStatus,
        String httpStatusText,
        Long dnsMs,
        Long tcpMs,
        Long tlsMs,
        Long ttfbMs,
        Long totalMs,
        Long responseBytes,
        boolean certificateValid,
        String certificateSubject,
        String certificateIssuer,
        OffsetDateTime certificateValidFrom,
        OffsetDateTime certificateValidTo,
        Long certificateDaysRemaining,
        List<SecurityHeaderResult> securityHeaders,
        List<String> risks,
        String riskLevel,
        String summary
) {
}
