package com.mindmap.academic.dto;

public record ManualAssignmentRequest(
        String course,
        String title,
        String status,
        String deadline,
        boolean timed,
        Integer timeLimitMinutes,
        String note
) {
}
