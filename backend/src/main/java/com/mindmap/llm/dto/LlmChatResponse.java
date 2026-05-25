package com.mindmap.llm.dto;

import java.time.OffsetDateTime;

public record LlmChatResponse(
        String provider,
        String model,
        String content,
        OffsetDateTime createdAt
) {
}
