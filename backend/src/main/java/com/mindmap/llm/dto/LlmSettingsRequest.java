package com.mindmap.llm.dto;

public record LlmSettingsRequest(
        String provider,
        String baseUrl,
        String apiPath,
        String apiKey,
        String model,
        String authType
) {
}
