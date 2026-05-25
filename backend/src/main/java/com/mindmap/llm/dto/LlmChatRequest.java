package com.mindmap.llm.dto;

import java.util.List;

public record LlmChatRequest(
        String provider,
        String model,
        String baseUrl,
        String message,
        List<LlmMessage> messages,
        Double temperature,
        Integer maxTokens,
        String apiKey,
        String apiPath,
        String authType
) {
}
