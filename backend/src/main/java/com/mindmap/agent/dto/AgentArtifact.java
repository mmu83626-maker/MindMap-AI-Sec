package com.mindmap.agent.dto;

public record AgentArtifact(
        String id,
        String filename,
        String type,
        String url,
        long sizeBytes
) {
}
