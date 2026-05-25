package com.mindmap.agent.dto;

import java.util.Map;

public record AgentCommandRequest(
        String command,
        String channel,
        String userId,
        String chatId,
        Map<String, Object> context,
        Map<String, Object> skillParameters
) {
}
