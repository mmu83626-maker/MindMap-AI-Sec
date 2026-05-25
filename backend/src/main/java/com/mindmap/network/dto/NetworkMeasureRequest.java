package com.mindmap.network.dto;

import java.util.List;

public record NetworkMeasureRequest(
        String url,
        List<String> checks,
        Integer samples
) {
}
