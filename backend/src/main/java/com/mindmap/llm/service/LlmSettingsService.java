package com.mindmap.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindmap.llm.dto.LlmRuntimeSettings;
import com.mindmap.llm.dto.LlmSettingsRequest;
import com.mindmap.llm.dto.LlmSettingsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class LlmSettingsService {

    private final ObjectMapper objectMapper;
    private final Path storagePath;

    @Value("${app.llm.default-provider:openai}")
    private String defaultProvider;

    @Value("${app.llm.openai.api-key:}")
    private String openaiApiKey;

    @Value("${app.llm.openai.base-url:https://api.openai.com}")
    private String openaiBaseUrl;

    @Value("${app.llm.openai.model:gpt-4o-mini}")
    private String openaiModel;

    public LlmSettingsService(
            ObjectMapper objectMapper,
            @Value("${app.llm.settings-path:${user.dir}/data/llm-settings.json}") String storagePath
    ) {
        this.objectMapper = objectMapper;
        this.storagePath = Path.of(storagePath);
    }

    public synchronized LlmRuntimeSettings current() {
        LlmRuntimeSettings saved = readSaved();
        if (saved != null) {
            return withDefaults(saved);
        }
        return withDefaults(new LlmRuntimeSettings(
                defaultProvider,
                openaiBaseUrl,
                "",
                openaiApiKey,
                openaiModel,
                "bearer",
                !blank(openaiApiKey)
        ));
    }

    public synchronized LlmSettingsResponse publicSettings() {
        return toResponse(current());
    }

    public synchronized LlmRuntimeSettings save(LlmSettingsRequest request) {
        LlmRuntimeSettings previous = current();
        String apiKey = firstNonBlank(request.apiKey(), previous.apiKey());
        LlmRuntimeSettings next = withDefaults(new LlmRuntimeSettings(
                firstNonBlank(request.provider(), previous.provider(), "custom"),
                firstNonBlank(request.baseUrl(), previous.baseUrl()),
                nullToBlank(request.apiPath()),
                apiKey,
                firstNonBlank(request.model(), previous.model(), "deepseek"),
                firstNonBlank(request.authType(), previous.authType(), "auto"),
                !blank(apiKey)
        ));
        write(next);
        return next;
    }

    public LlmSettingsResponse toResponse(LlmRuntimeSettings settings) {
        return new LlmSettingsResponse(
                settings.provider(),
                settings.baseUrl(),
                settings.apiPath(),
                settings.model(),
                settings.authType(),
                !blank(settings.apiKey()),
                resolveChatUrl(settings.baseUrl(), settings.apiPath()),
                maskSecret(settings.apiKey()),
                "runtime"
        );
    }

    public String resolveChatUrl(String baseUrl, String apiPath) {
        String url = nullToBlank(baseUrl).trim();
        String path = nullToBlank(apiPath).trim();
        if (url.isBlank()) {
            return "";
        }
        if (url.endsWith("/chat/completions") || url.endsWith("/api/chat") || url.endsWith("/api/generate")) {
            return url;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (path.isBlank() || "auto".equalsIgnoreCase(path)) {
            if (url.contains("api.deepseek.com")) {
                return url + "/chat/completions";
            }
            if (url.endsWith("/api/v1") || url.endsWith("/v1")) {
                return url + "/chat/completions";
            }
            return url + "/v1/chat/completions";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (url.endsWith("/v1") && path.startsWith("/v1/")) {
            path = path.substring(3);
        }
        return url + path;
    }

    private LlmRuntimeSettings withDefaults(LlmRuntimeSettings settings) {
        String baseUrl = firstNonBlank(settings.baseUrl(), openaiBaseUrl, "https://api.openai.com");
        String apiPath = nullToBlank(settings.apiPath());
        return new LlmRuntimeSettings(
                firstNonBlank(settings.provider(), "custom"),
                baseUrl,
                apiPath,
                nullToBlank(settings.apiKey()),
                firstNonBlank(settings.model(), openaiModel, "deepseek"),
                firstNonBlank(settings.authType(), "auto"),
                !blank(settings.apiKey())
        );
    }

    private LlmRuntimeSettings readSaved() {
        if (!Files.exists(storagePath)) {
            return null;
        }
        try {
            return objectMapper.readValue(storagePath.toFile(), LlmRuntimeSettings.class);
        } catch (IOException ex) {
            return null;
        }
    }

    private void write(LlmRuntimeSettings settings) {
        try {
            Files.createDirectories(storagePath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storagePath.toFile(), settings);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to save LLM settings", ex);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!blank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String maskSecret(String value) {
        if (blank(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, Math.min(4, trimmed.length())) + "..." + trimmed.substring(trimmed.length() - 4);
    }
}
