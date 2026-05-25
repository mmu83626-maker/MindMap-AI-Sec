package com.mindmap.agent.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AgentRunRecord(
        String id,
        String channel,
        String userId,
        String chatId,
        String command,
        String answer,
        List<String> plannedSkills,
        OffsetDateTime createdAt
) {
}
