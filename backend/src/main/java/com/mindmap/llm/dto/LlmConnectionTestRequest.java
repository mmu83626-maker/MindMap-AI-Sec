package com.mindmap.llm.dto;

public record LlmConnectionTestRequest(
        String provider,
        String baseUrl,
        String apiPath,
        String apiKey,
        String model,
        String authType,
        String message
) {
}
