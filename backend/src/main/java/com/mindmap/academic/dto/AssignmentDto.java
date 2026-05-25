package com.mindmap.academic.dto;

import java.time.OffsetDateTime;

public record AssignmentDto(
        String id,
        String platform,
        String course,
        String title,
        String status,
        OffsetDateTime deadline,
        String sourceUrl,
        boolean timed,
        Integer timeLimitMinutes,
        String note
) {
}
