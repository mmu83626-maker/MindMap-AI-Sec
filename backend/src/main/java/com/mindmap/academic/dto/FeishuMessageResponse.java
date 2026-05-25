package com.mindmap.academic.dto;

import java.time.OffsetDateTime;

public record FeishuMessageResponse(
        boolean sent,
        String channel,
        String preview,
        OffsetDateTime createdAt
) {
}
