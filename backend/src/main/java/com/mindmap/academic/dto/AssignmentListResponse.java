package com.mindmap.academic.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AssignmentListResponse(
        String source,
        String status,
        String message,
        OffsetDateTime syncedAt,
        List<AssignmentDto> assignments
) {
}
