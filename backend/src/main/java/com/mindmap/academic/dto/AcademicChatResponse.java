package com.mindmap.academic.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AcademicChatResponse(
        String answer,
        List<String> actions,
        List<AssignmentDto> relatedAssignments,
        OffsetDateTime createdAt
) {
}
