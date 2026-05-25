package com.mindmap.llm.dto;

public record LlmProviderInfo(
        String provider,
        String displayName,
        String baseUrl,
        String defaultModel,
        boolean configured
) {
}
