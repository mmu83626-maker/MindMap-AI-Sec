package com.mindmap.llm.dto;

public record LlmRuntimeSettings(
        String provider,
        String baseUrl,
        String apiPath,
        String apiKey,
        String model,
        String authType,
        boolean configured
) {
}
