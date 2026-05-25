package com.mindmap.agent.dto;

import java.util.List;

public record SkillDefinition(
        String name,
        String title,
        String description,
        List<String> triggerWords,
        boolean enabled,
        List<SkillParameterDefinition> parameters,
        String sourceUrl,
        String signature,
        String signatureStatus
) {
}
