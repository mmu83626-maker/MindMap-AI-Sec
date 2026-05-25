package com.mindmap.llm.dto;

import java.time.OffsetDateTime;

public record LlmConnectionTestResponse(
        boolean ok,
        String provider,
        String model,
        String resolvedUrl,
        String message,
        OffsetDateTime checkedAt
) {
}
