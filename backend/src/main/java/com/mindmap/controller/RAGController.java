package com.mindmap.controller;

import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

@RestController
@RequestMapping("/api/rag")
@CrossOrigin(origins = "*")
public class RAGController {

    @GetMapping("/ask")
    public Map<String, Object> ask(@RequestParam String query) {
        Map<String, Object> response = new HashMap<>();
        response.put("question", query);
        response.put("answer", "This is a sample RAG response. In production, this would be powered by LangChain4j.");
        response.put("sources", Arrays.asList(
            Map.of("title", "Source 1", "url", "https://example.com/1")
        ));
        return response;
    }

    @GetMapping("/sources")
    public Map<String, Object> getSources(@RequestParam String query) {
        Map<String, Object> response = new HashMap<>();
        response.put("query", query);
        response.put("sources", Arrays.asList(
            Map.of("id", "1", "title", "Research Paper 1", "excerpt", "Sample excerpt...", "url", "https://example.com/1")
        ));
        return response;
    }
}
