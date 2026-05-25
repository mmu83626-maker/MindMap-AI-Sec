package com.mindmap.agent.dto;

public record SkillPackage(
        SkillDefinition skill,
        String signature,
        String sourceUrl
) {
}
