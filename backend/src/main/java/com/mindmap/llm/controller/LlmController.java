package com.mindmap.llm.controller;

import com.mindmap.llm.dto.LlmChatRequest;
import com.mindmap.llm.dto.LlmChatResponse;
import com.mindmap.llm.dto.LlmConnectionTestRequest;
import com.mindmap.llm.dto.LlmConnectionTestResponse;
import com.mindmap.llm.dto.LlmProviderInfo;
import com.mindmap.llm.dto.LlmRuntimeSettings;
import com.mindmap.llm.dto.LlmSettingsRequest;
import com.mindmap.llm.dto.LlmSettingsResponse;
import com.mindmap.llm.service.LlmGatewayService;
import com.mindmap.llm.service.LlmSettingsService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/llm")
@CrossOrigin(origins = "*")
public class LlmController {

    private final LlmGatewayService llmGatewayService;
    private final LlmSettingsService llmSettingsService;

    public LlmController(LlmGatewayService llmGatewayService, LlmSettingsService llmSettingsService) {
        this.llmGatewayService = llmGatewayService;
        this.llmSettingsService = llmSettingsService;
    }

    @GetMapping("/providers")
    public List<LlmProviderInfo> providers() {
        return llmGatewayService.providers();
    }

    @PostMapping("/chat")
    public LlmChatResponse chat(@RequestBody LlmChatRequest request) {
        return llmGatewayService.chat(request);
    }

    @GetMapping("/settings")
    public LlmSettingsResponse settings() {
        return llmSettingsService.publicSettings();
    }

    @PutMapping("/settings")
    public LlmSettingsResponse saveSettings(@RequestBody LlmSettingsRequest request) {
        LlmRuntimeSettings saved = llmSettingsService.save(request);
        return llmSettingsService.toResponse(saved);
    }

    @PostMapping("/test")
    public LlmConnectionTestResponse test(@RequestBody LlmConnectionTestRequest request) {
        LlmRuntimeSettings runtime = llmSettingsService.current();
        String baseUrl = firstNonBlank(request.baseUrl(), runtime.baseUrl());
        String apiPath = firstNonBlank(request.apiPath(), runtime.apiPath());
        String model = firstNonBlank(request.model(), runtime.model());
        String resolvedUrl = llmSettingsService.resolveChatUrl(baseUrl, apiPath);
        try {
            LlmChatResponse response = llmGatewayService.chat(new LlmChatRequest(
                    request.provider(),
                    model,
                    baseUrl,
                    request.message() == null || request.message().isBlank() ? "Reply with OK only." : request.message(),
                    null,
                    0.1,
                    64,
                    request.apiKey(),
                    apiPath,
                    firstNonBlank(request.authType(), runtime.authType())
            ));
            return new LlmConnectionTestResponse(
                    true,
                    response.provider(),
                    response.model(),
                    resolvedUrl,
                    response.content(),
                    OffsetDateTime.now()
            );
        } catch (ResponseStatusException ex) {
            return new LlmConnectionTestResponse(
                    false,
                    firstNonBlank(request.provider(), runtime.provider()),
                    model,
                    resolvedUrl,
                    ex.getReason() == null ? ex.getMessage() : ex.getReason(),
                    OffsetDateTime.now()
            );
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
}
