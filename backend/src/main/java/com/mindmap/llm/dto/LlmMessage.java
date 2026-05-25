package com.mindmap.llm.dto;

public record LlmMessage(
        String role,
        String content
) {
}
