package com.mindmap.academic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindmap.academic.dto.AssignmentDto;
import com.mindmap.academic.dto.FeishuMessageResponse;
import com.mindmap.academic.dto.ManualAssignmentRequest;
import com.mindmap.agent.dto.AgentCommandRequest;
import com.mindmap.agent.dto.AgentCommandResponse;
import com.mindmap.agent.service.AgentOrchestratorService;
import com.mindmap.llm.dto.LlmRuntimeSettings;
import com.mindmap.llm.dto.LlmSettingsRequest;
import com.mindmap.llm.service.LlmSettingsService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FeishuService {

    private static final Logger log = LoggerFactory.getLogger(FeishuService.class);
    private static final DateTimeFormatter DEADLINE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DIGEST_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final AssignmentService assignmentService;
    private final AgentOrchestratorService agentOrchestratorService;
    private final LlmSettingsService llmSettingsService;
    private final FeishuChatBindingStore chatBindingStore;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ExecutorService callbackExecutor;

    @Value("${app.feishu.app-id:}")
    private String appId;

    @Value("${app.feishu.app-secret:}")
    private String appSecret;

    @Value("${app.feishu.default-chat-id:}")
    private String defaultChatId;

    @Value("${app.feishu.verification-token:}")
    private String verificationToken;

    @Value("${app.feishu.public-base-url:}")
    private String publicBaseUrl;

    public FeishuService(
            AssignmentService assignmentService,
            AgentOrchestratorService agentOrchestratorService,
            LlmSettingsService llmSettingsService,
            FeishuChatBindingStore chatBindingStore,
            ObjectMapper objectMapper
    ) {
        this.assignmentService = assignmentService;
        this.agentOrchestratorService = agentOrchestratorService;
        this.llmSettingsService = llmSettingsService;
        this.chatBindingStore = chatBindingStore;
        this.objectMapper = objectMapper;
        this.callbackExecutor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "feishu-callback-processor");
            thread.setDaemon(true);
            return thread;
        });
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public FeishuMessageResponse sendAssignmentReminder(String assignmentId, String chatId, String userKey, String customMessage) {
        AssignmentDto assignment = assignmentService.findById(assignmentId);
        String target = resolveTargetChatId(chatId, userKey);
        String preview = buildPreview(assignment, customMessage, target);

        if (assignment == null || target == null || target.isBlank() || !hasBotCredentials()) {
            return new FeishuMessageResponse(false, "feishu-preview", preview, OffsetDateTime.now());
        }

        sendTextToChat(target, preview);
        return new FeishuMessageResponse(true, "feishu", preview, OffsetDateTime.now());
    }

    public Map<String, Object> handleEvent(Map<String, Object> payload) {
        String type = asString(payload.get("type"));
        if ("url_verification".equals(type)) {
            validateVerificationToken(asString(payload.get("token")));
            return Map.of("challenge", asString(payload.get("challenge")));
        }

        JsonNode root = objectMapper.valueToTree(payload);
        String schema = root.path("schema").asText("");
        if ("2.0".equals(schema) && root.has("header")) {
            validateVerificationToken(root.path("header").path("token").asText(""));
            String eventType = root.path("header").path("event_type").asText("");
            if ("im.message.receive_v1".equals(eventType)) {
                CompletableFuture.runAsync(() -> handleIncomingMessage(root.path("event")), callbackExecutor)
                        .exceptionally(ex -> {
                            log.warn("Failed to process Feishu message event", ex);
                            return null;
                        });
            }
            return Map.of("status", "received", "eventType", eventType, "processing", "scheduled");
        }

        return Map.of("status", "received");
    }

    private void handleIncomingMessage(JsonNode event) {
        try {
            String chatId = event.path("message").path("chat_id").asText("");
            String messageId = event.path("message").path("message_id").asText("");
            String messageType = event.path("message").path("message_type").asText("");
            String rawContent = event.path("message").path("content").asText("");
            String userKey = extractUserKey(event);
            if (chatId.isBlank() || !"text".equals(messageType)) {
                log.debug("Ignored Feishu event: chatId={}, messageType={}", chatId, messageType);
                return;
            }

            if (!userKey.isBlank()) {
                chatBindingStore.remember(userKey, chatId, extractDisplayName(event));
            }

            String text = stripBotMention(extractText(rawContent)).trim();
            if (text.isBlank()) {
                return;
            }

            String answer = handleCommand(text, chatId);
            if (hasBotCredentials()) {
                replyOrSend(messageId, chatId, answer);
            } else {
                log.warn("Feishu auto-reply skipped because FEISHU_APP_ID or FEISHU_APP_SECRET is missing");
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to handle Feishu incoming message", ex);
        }
    }

    public Map<String, Object> health() {
        String publicCallbackUrl = currentPublicCallbackUrl();
        return Map.of(
                "status", "ok",
                "callbackPath", "/api/feishu/events",
                "publicCallbackUrl", publicCallbackUrl,
                "requestUrl", publicCallbackUrl,
                "botCredentialsReady", hasBotCredentials(),
                "verificationTokenConfigured", verificationToken != null && !verificationToken.isBlank(),
                "defaultChatConfigured", defaultChatId != null && !defaultChatId.isBlank(),
                "savedUserChats", chatBindingStore.size(),
                "replyMode", hasBotCredentials() ? "live" : "preview-only",
                "recommendedNextFeatures", List.of(
                        "消息会先异步处理再回复，避免飞书回调超时。",
                        "机器人会记住最近的 chat_id，便于后续从网页侧把分析结果发回同一会话。",
                        "可发送：帮助、检测网站、开启监听、导出网络报告、生成 Excel 对比。"
                )
        );
    }

    private String resolveTargetChatId(String chatId, String userKey) {
        if (chatId != null && !chatId.isBlank()) {
            return chatId;
        }
        if (userKey != null && !userKey.isBlank()) {
            String resolved = chatBindingStore.findChatId(userKey).orElse("");
            if (!resolved.isBlank()) {
                return resolved;
            }
        }
        return defaultChatId;
    }

    private String handleCommand(String text, String chatId) {
        if (isApiConfigCommand(text)) {
            return configureApiFromCommand(text);
        }

        if (containsAny(text, "帮助", "help", "菜单")) {
            return """
                    NetScope AI 网络 Agent 已连接。
                    你可以直接像使用 OpenClaw 一样发自然语言指令：
                    - 检测 https://example.com 的 HTTPS、TLS 证书和安全响应头
                    - 开启 Web 请求监听，并导出 Excel 日志
                    - 把当前网络检测结果整理成 Word 报告
                    - 对比 https://example.com 和另一个网站的安全性与性能
                    """;
        }

        if (containsAny(text, "添加作业", "查作业", "最近作业", "规划学习", "规划今晚", "复习计划")) {
            return """
                    当前飞书机器人已切换为计算机网络方向，不再处理作业录入或学习规划。
                    你可以发送：
                    - 检测 https://example.com 的 HTTPS、TLS 证书和安全响应头
                    - 开启 Web 请求监听，并导出 Excel 日志
                    - 对比两个网站的安全性与性能
                    """;
        }

        AgentCommandResponse response = agentOrchestratorService.run(new AgentCommandRequest(
                text,
                "feishu",
                null,
                chatId,
                Map.of(),
                Map.of()
        ));
        return absolutizeArtifactLinks(response.answer());
    }

    private boolean isApiConfigCommand(String text) {
        String lower = text.toLowerCase();
        return lower.startsWith("配置api")
                || lower.startsWith("配置 api")
                || lower.startsWith("set api")
                || lower.startsWith("api key")
                || lower.startsWith("apikey");
    }

    private String configureApiFromCommand(String text) {
        String apiKey = extractApiKey(text);
        if (apiKey.isBlank()) {
            return "没有识别到 API Key。可以这样发：配置API sk-xxxxxxxx";
        }

        boolean schoolGateway = containsAny(text, "学校", "本地", "网关", "117.145");
        LlmRuntimeSettings saved = llmSettingsService.save(new LlmSettingsRequest(
                "custom",
                schoolGateway ? "http://117.145.189.131:48081" : "https://api.deepseek.com",
                schoolGateway ? "/api/v1/chat/completions" : "/chat/completions",
                apiKey,
                schoolGateway ? "deepseek" : "deepseek-v4-flash",
                schoolGateway ? "auto" : "bearer"
        ));
        String target = llmSettingsService.toResponse(saved).resolvedUrl();
        return "已更新大模型 API 配置。\n"
                + "接口：" + target + "\n"
                + "模型：" + saved.model() + "\n"
                + "Key：" + maskSecret(apiKey) + "\n"
                + "现在飞书和网页 Agent 都会使用这套配置。";
    }

    private String extractApiKey(String text) {
        Matcher matcher = Pattern.compile("(?i)(sk-[a-z0-9_\\-]{12,}|[a-z0-9_\\-]{20,})").matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String maskSecret(String value) {
        if (value == null || value.length() < 10) {
            return "******";
        }
        return value.substring(0, Math.min(6, value.length())) + "..." + value.substring(value.length() - 4);
    }

    private String addAssignmentFromCommand(String command) {
        String[] parts = command.split("\\|");
        if (parts.length < 3) {
            return "格式不完整。请发送：添加作业 课程|作业|2026-05-12 23:59|限时60|备注";
        }
        boolean timed = parts.length >= 4 && parts[3].contains("限时");
        Integer minutes = timed ? parseMinutes(parts[3]) : null;
        AssignmentDto assignment = assignmentService.addManualAssignment(new ManualAssignmentRequest(
                parts[0].trim(),
                parts[1].trim(),
                "待完成",
                parts[2].trim(),
                timed,
                minutes,
                parts.length >= 5 ? parts[4].trim() : ""
        ));
        return "已添加作业：\n" + assignment.course() + " - " + assignment.title()
                + "\n截止：" + assignment.deadline().format(DEADLINE_FORMATTER)
                + (assignment.timed() ? "\n限时：" + assignment.timeLimitMinutes() + " 分钟" : "");
    }

    private String assignmentDigest(int limit) {
        List<AssignmentDto> assignments = assignmentService.upcomingAssignments(limit);
        if (assignments.isEmpty()) {
            return "当前没有作业。";
        }

        StringBuilder builder = new StringBuilder("最近作业：");
        for (AssignmentDto assignment : assignments) {
            builder.append("\n- ")
                    .append(assignment.course())
                    .append("：")
                    .append(assignment.title())
                    .append("，截止 ")
                    .append(assignment.deadline().format(DIGEST_FORMATTER))
                    .append(assignment.timed() ? "，限时 " + assignment.timeLimitMinutes() + " 分钟" : "")
                    .append("，状态：")
                    .append(assignment.status());
        }
        return builder.toString();
    }

    private String buildStudyPlan(List<AssignmentDto> assignments) {
        if (assignments.isEmpty()) {
            return "当前没有作业，可以安排复习、整理笔记，或发送“添加作业”录入新的任务。";
        }

        StringBuilder builder = new StringBuilder("建议今晚这样安排：");
        int index = 1;
        for (AssignmentDto assignment : assignments) {
            builder.append("\n")
                    .append(index++)
                    .append(". ")
                    .append(assignment.course())
                    .append(" - ")
                    .append(assignment.title())
                    .append("：先确认要求，再完成主体内容，最后预留 20 分钟检查。")
                    .append(assignment.timed() ? "这是限时任务，建议先做一次模拟。" : "");
        }
        return builder.toString();
    }

    private void replyOrSend(String messageId, String chatId, String text) {
        if (messageId != null && !messageId.isBlank()) {
            try {
                sendTextReply(messageId, text);
                return;
            } catch (ResponseStatusException ex) {
                log.warn("Feishu message reply failed, falling back to chat send: {}", ex.getReason());
            }
        }
        sendTextToChat(chatId, text);
    }

    public void sendTextToChat(String chatId, String text) {
        if (!hasBotCredentials()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FEISHU_APP_ID and FEISHU_APP_SECRET are required");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receive_id", chatId);
        body.put("msg_type", "text");
        body.put("content", toJsonString(Map.of("text", text)));

        postFeishuJson("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id", body);
    }

    private void sendTextReply(String messageId, String text) {
        if (!hasBotCredentials()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FEISHU_APP_ID and FEISHU_APP_SECRET are required");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", "text");
        body.put("content", toJsonString(Map.of("text", text)));

        postFeishuJson("https://open.feishu.cn/open-apis/im/v1/messages/" + messageId + "/reply", body);
    }

    private void postFeishuJson(String url, Map<String, Object> body) {
        String tenantAccessToken = getTenantAccessToken();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + tenantAccessToken)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertFeishuOk(response);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to call Feishu API", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Feishu API request was interrupted", e);
        }
    }

    private String getTenantAccessToken() {
        Map<String, String> body = Map.of("app_id", appId, "app_secret", appSecret);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertFeishuOk(response);

            JsonNode root = objectMapper.readTree(response.body());
            String token = root.path("tenant_access_token").asText("");
            if (token.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Feishu did not return tenant_access_token");
            }
            return token;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to get Feishu tenant access token", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Feishu token request was interrupted", e);
        }
    }

    private void assertFeishuOk(HttpResponse<String> response) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Feishu returned HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        int code = root.path("code").asInt(0);
        if (code != 0) {
            String message = root.path("msg").asText(response.body());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Feishu returned code " + code + ": " + message);
        }
    }

    private String buildPreview(AssignmentDto assignment, String customMessage, String target) {
        if (assignment == null) {
            return "未找到作业，已跳过发送。";
        }

        String base = "【作业提醒】" + assignment.course() + " - " + assignment.title()
                + "，截止 " + assignment.deadline().format(DEADLINE_FORMATTER)
                + (assignment.timed() ? "，限时 " + assignment.timeLimitMinutes() + " 分钟" : "");
        if (customMessage != null && !customMessage.isBlank()) {
            base = customMessage + "\n" + base;
        }
        if (target == null || target.isBlank()) {
            return base + "\n未配置 FEISHU_DEFAULT_CHAT_ID，当前仅生成消息预览。";
        }
        if (!hasBotCredentials()) {
            return base + "\n未配置 FEISHU_APP_ID / FEISHU_APP_SECRET，当前仅生成消息预览。";
        }
        return base;
    }

    private void validateVerificationToken(String token) {
        if (verificationToken == null || verificationToken.isBlank()) {
            return;
        }
        if (!verificationToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Feishu verification token");
        }
    }

    private boolean hasBotCredentials() {
        return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
    }

    private String toJsonString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to encode Feishu content", e);
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String extractUserKey(JsonNode event) {
        String tenantKey = event.path("tenant_key").asText("");
        String userId = firstNonBlank(
                event.path("sender").path("sender_id").path("open_id").asText(""),
                event.path("sender").path("sender_id").path("user_id").asText(""),
                event.path("sender").path("sender_id").path("union_id").asText(""),
                event.path("sender").path("open_id").asText(""),
                event.path("sender").path("user_id").asText(""),
                event.path("sender").path("union_id").asText("")
        );
        if (userId.isBlank()) {
            return "";
        }
        if (tenantKey.isBlank()) {
            return userId;
        }
        return tenantKey + ":" + userId;
    }

    private String extractDisplayName(JsonNode event) {
        return firstNonBlank(
                event.path("sender").path("sender_id").path("user_id").asText(""),
                event.path("sender").path("sender_id").path("open_id").asText(""),
                event.path("sender").path("sender_id").path("union_id").asText("")
        );
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private String extractText(String rawContent) {
        try {
            JsonNode content = objectMapper.readTree(rawContent);
            return content.path("text").asText("");
        } catch (IOException e) {
            return rawContent;
        }
    }

    private String stripBotMention(String text) {
        return text.replaceAll("@_user_\\d+", "").replaceAll("@[^\\s]+", "").trim();
    }

    private Integer parseMinutes(String value) {
        String digits = value.replaceAll("[^0-9]", "");
        return digits.isBlank() ? 60 : Integer.parseInt(digits);
    }

    private boolean containsAny(String text, String... keywords) {
        String lower = text.toLowerCase();
        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String currentPublicCallbackUrl() {
        String configured = trimTrailingSlash(publicBaseUrl);
        if (!configured.isBlank()) {
            return configured + "/api/feishu/events";
        }
        String tunnel = latestTryCloudflareUrl();
        if (!tunnel.isBlank()) {
            return tunnel + "/api/feishu/events";
        }
        return "/api/feishu/events";
    }

    private String absolutizeArtifactLinks(String answer) {
        if (answer == null || !answer.contains("/api/agent/artifacts/")) {
            return answer;
        }
        String root = currentPublicRoot();
        return root.isBlank() ? answer : answer.replace("：/api/agent/artifacts/", "：" + root + "/api/agent/artifacts/");
    }

    private String currentPublicRoot() {
        String configured = trimTrailingSlash(publicBaseUrl);
        if (!configured.isBlank()) {
            return configured;
        }
        return latestTryCloudflareUrl();
    }

    private String latestTryCloudflareUrl() {
        Pattern pattern = Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com");
        for (Path path : List.of(Path.of("cloudflared-8090.err.log"), Path.of("../cloudflared-8090.err.log"))) {
            if (!Files.exists(path)) {
                continue;
            }
            try {
                String content = Files.readString(path);
                Matcher matcher = pattern.matcher(content);
                String latest = "";
                while (matcher.find()) {
                    latest = matcher.group();
                }
                if (!latest.isBlank()) {
                    return trimTrailingSlash(latest);
                }
            } catch (IOException ex) {
                log.debug("Failed to read cloudflared log {}", path, ex);
            }
        }
        return "";
    }

    private String trimTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    @PreDestroy
    public void shutdown() {
        callbackExecutor.shutdownNow();
    }
}

