package com.mindmap.agent.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record SkillExecutionRecord(
        String id,
        String runId,
        String skillName,
        String skillTitle,
        String status,
        Map<String, Object> parameters,
        String summary,
        long durationMs,
        OffsetDateTime createdAt
) {
}
