package com.mindmap.agent.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record AgentCommandResponse(
        String answer,
        List<String> plannedSkills,
        Map<String, Object> data,
        OffsetDateTime createdAt
) {
}
