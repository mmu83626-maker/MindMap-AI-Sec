package com.mindmap.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindmap.llm.dto.LlmChatRequest;
import com.mindmap.llm.dto.LlmChatResponse;
import com.mindmap.llm.dto.LlmMessage;
import com.mindmap.llm.dto.LlmProviderInfo;
import com.mindmap.llm.dto.LlmRuntimeSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class LlmGatewayService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final LlmSettingsService settingsService;

    @Value("${app.llm.default-provider:openai}")
    private String defaultProvider;

    @Value("${app.llm.demo-fallback-enabled:true}")
    private boolean demoFallbackEnabled;

    @Value("${app.llm.openai.api-key:}")
    private String openaiApiKey;

    @Value("${app.llm.openai.base-url:https://api.openai.com/v1/chat/completions}")
    private String openaiBaseUrl;

    @Value("${app.llm.openai.model:gpt-4o-mini}")
    private String openaiModel;

    @Value("${app.llm.kimi.api-key:}")
    private String kimiApiKey;

    @Value("${app.llm.kimi.base-url:https://api.moonshot.ai/v1/chat/completions}")
    private String kimiBaseUrl;

    @Value("${app.llm.kimi.model:moonshot-v1-8k}")
    private String kimiModel;

    @Value("${app.llm.doubao.api-key:}")
    private String doubaoApiKey;

    @Value("${app.llm.doubao.base-url:https://ark.cn-beijing.volces.com/api/v3/chat/completions}")
    private String doubaoBaseUrl;

    @Value("${app.llm.doubao.model:}")
    private String doubaoModel;

    public LlmGatewayService(ObjectMapper objectMapper, LlmSettingsService settingsService) {
        this.objectMapper = objectMapper;
        this.settingsService = settingsService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public List<LlmProviderInfo> providers() {
        LlmRuntimeSettings runtime = settingsService.current();
        return List.of(
                providerInfo("custom", "DeepSeek / OpenAI Compatible", runtime.baseUrl(), runtime.model(), runtime.apiKey()),
                providerInfo("openai", "OpenAI", openaiBaseUrl, openaiModel, openaiApiKey),
                providerInfo("kimi", "Kimi / Moonshot", kimiBaseUrl, kimiModel, kimiApiKey),
                providerInfo("doubao", "豆包 / 火山方舟", doubaoBaseUrl, doubaoModel, doubaoApiKey)
        );
    }

    public LlmChatResponse chat(LlmChatRequest request) {
        ProviderConfig provider = resolveProvider(request.provider());
        LlmRuntimeSettings runtime = settingsService.current();
        String apiKey = firstNonBlank(request.apiKey(), provider.apiKey(), runtime.apiKey());
        if (isBlank(apiKey)) {
            if (demoFallbackEnabled) {
                String model = firstNonBlank(request.model(), provider.defaultModel(), runtime.model(), "demo-fallback");
                return new LlmChatResponse(provider.name(), model, fallbackContent(request, "missing API key"), OffsetDateTime.now());
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Missing API key for provider: " + provider.name()
            );
        }

        String model = firstNonBlank(request.model(), provider.defaultModel(), runtime.model(), "default");
        if (isBlank(model)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Missing model for provider: " + provider.name()
            );
        }

        List<LlmMessage> messages = normalizeMessages(request);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("temperature", request.temperature() == null ? 0.7 : request.temperature());
        if (request.maxTokens() != null) {
            payload.put("max_tokens", request.maxTokens());
        }

        String baseUrl = normalizeChatCompletionsUrl(
                firstNonBlank(request.baseUrl(), provider.baseUrl(), runtime.baseUrl()),
                firstNonBlank(request.apiPath(), runtime.apiPath())
        );
        String authType = firstNonBlank(request.authType(), runtime.authType(), "bearer");
        String content;
        try {
            content = callChatCompletions(baseUrl, apiKey, authType, payload);
        } catch (ResponseStatusException ex) {
            if (!demoFallbackEnabled) {
                throw ex;
            }
            String reason = ex.getReason() == null ? ex.getMessage() : ex.getReason();
            content = fallbackContent(request, reason);
        }
        return new LlmChatResponse(provider.name(), model, content, OffsetDateTime.now());
    }

    private String fallbackContent(LlmChatRequest request, String reason) {
        String prompt = firstNonBlank(request.message(), lastUserMessage(request.messages()), "demo request");
        return "Demo fallback reply: robot chat API is reachable. "
                + "The external LLM provider is not available right now"
                + (isBlank(reason) ? "." : " (" + reason + ").")
                + " User message: " + prompt;
    }

    private String lastUserMessage(List<LlmMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            LlmMessage message = messages.get(i);
            if (message != null && "user".equalsIgnoreCase(message.role())) {
                return message.content();
            }
        }
        return "";
    }

    private String callChatCompletions(String baseUrl, String apiKey, String authType, Map<String, Object> payload) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
            String normalizedAuthType = firstNonBlank(authType, "bearer").toLowerCase(Locale.ROOT);
            if ("x-api-key".equals(normalizedAuthType)
                    || ("auto".equals(normalizedAuthType) && baseUrl.contains("/api/"))) {
                requestBuilder.header("X-API-Key", apiKey);
            } else {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpRequest httpRequest = requestBuilder.build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "LLM provider returned " + response.statusCode() + ": " + response.body()
                );
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "LLM provider returned an empty response");
            }
            return content.asText();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to call LLM provider", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "LLM request was interrupted", e);
        }
    }

    private List<LlmMessage> normalizeMessages(LlmChatRequest request) {
        if (request.messages() != null && !request.messages().isEmpty()) {
            return request.messages();
        }

        String message = request.message() == null ? "" : request.message().trim();
        if (message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message or messages is required");
        }

        List<LlmMessage> messages = new ArrayList<>();
        messages.add(new LlmMessage("system", "你是 NetScope AI 的计算机网络安全助手。回答要清晰、可执行，并优先关注网络安全、网络测量、Web 应用监听、HTTPS/TLS、安全响应头和报告生成。不要主动输出作业、课程、截止时间或学习计划相关内容。"));
        messages.add(new LlmMessage("user", message));
        return messages;
    }

    private ProviderConfig resolveProvider(String providerName) {
        LlmRuntimeSettings runtime = settingsService.current();
        String normalized = firstNonBlank(providerName, runtime.provider(), defaultProvider).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "custom", "openai-compatible", "compatible", "deepseek" -> {
                yield new ProviderConfig("custom", runtime.baseUrl(), runtime.apiKey(), runtime.model());
            }
            case "kimi", "moonshot" -> new ProviderConfig("kimi", kimiBaseUrl, kimiApiKey, kimiModel);
            case "doubao", "ark", "volcengine" -> new ProviderConfig("doubao", doubaoBaseUrl, doubaoApiKey, doubaoModel);
            case "openai" -> new ProviderConfig("openai", openaiBaseUrl, openaiApiKey, openaiModel);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported LLM provider: " + providerName);
        };
    }

    private LlmProviderInfo providerInfo(String provider, String displayName, String baseUrl, String model, String apiKey) {
        return new LlmProviderInfo(provider, displayName, baseUrl, model, !isBlank(apiKey));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeChatCompletionsUrl(String rawBaseUrl, String apiPath) {
        if (isBlank(rawBaseUrl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing LLM baseUrl");
        }

        String url = rawBaseUrl.trim();
        if (url.endsWith("/chat/completions")) {
            return url;
        }
        if (!isBlank(apiPath)) {
            return settingsService.resolveChatUrl(url, apiPath);
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/v1")) {
            return url + "/chat/completions";
        }
        return url + "/v1/chat/completions";
    }

    private record ProviderConfig(
            String name,
            String baseUrl,
            String apiKey,
            String defaultModel
    ) {
    }
}

