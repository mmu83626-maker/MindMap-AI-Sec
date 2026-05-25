package com.mindmap.llm.dto;

public record LlmSettingsResponse(
        String provider,
        String baseUrl,
        String apiPath,
        String model,
        String authType,
        boolean configured,
        String resolvedUrl,
        String apiKeyPreview,
        String updatedBy
) {
}
