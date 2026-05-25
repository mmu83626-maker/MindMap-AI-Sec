package com.mindmap.agent.dto;

public record SkillImportRequest(
        SkillDefinition skill,
        String json,
        String signature
) {
}
