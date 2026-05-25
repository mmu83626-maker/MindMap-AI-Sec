package com.mindmap.academic.dto;

import java.util.List;

public record AcademicChatRequest(
        String message,
        String channel,
        List<String> contextIds,
        String llmProvider,
        String llmModel,
        String llmBaseUrl,
        String apiKey,
        String apiPath,
        String authType
) {
}
