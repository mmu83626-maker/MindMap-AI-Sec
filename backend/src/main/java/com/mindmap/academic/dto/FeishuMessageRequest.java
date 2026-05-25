package com.mindmap.academic.dto;

public record FeishuMessageRequest(
        String assignmentId,
        String chatId,
        String userKey,
        String message
) {
}
