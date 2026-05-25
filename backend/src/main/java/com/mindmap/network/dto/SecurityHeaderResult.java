package com.mindmap.network.dto;

public record SecurityHeaderResult(
        String name,
        boolean present,
        String value,
        String meaning,
        String recommendation
) {
}
