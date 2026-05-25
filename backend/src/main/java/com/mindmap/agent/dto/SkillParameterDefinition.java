package com.mindmap.agent.dto;

import java.util.List;

public record SkillParameterDefinition(
        String name,
        String label,
        String type,
        String placeholder,
        boolean required,
        String defaultValue,
        List<String> options
) {
}
