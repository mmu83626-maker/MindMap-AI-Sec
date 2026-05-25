package com.mindmap.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindmap.agent.dto.SkillDefinition;
import com.mindmap.agent.dto.SkillPackage;
import com.mindmap.agent.dto.SkillParameterDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SkillRegistryService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final CopyOnWriteArrayList<SkillDefinition> skills;
    private final Path storagePath;
    private final List<String> allowedHosts;
    private final String signatureSecret;

    public SkillRegistryService(
            ObjectMapper objectMapper,
            @Value("${app.skills.storage-path:${user.dir}/data/skills.json}") String storagePath,
            @Value("${app.skills.allowed-hosts:localhost,127.0.0.1,raw.githubusercontent.com,github.com,gist.githubusercontent.com}") String allowedHosts,
            @Value("${app.skills.signature-secret:}") String signatureSecret
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        this.storagePath = Path.of(storagePath);
        this.allowedHosts = parseHosts(allowedHosts);
        this.signatureSecret = signatureSecret == null ? "" : signatureSecret.trim();
        this.skills = new CopyOnWriteArrayList<>(mergeStoredSkills(builtInSkills(), loadStoredSkills()));
    }

    public List<SkillDefinition> listSkills() {
        return skills.stream()
                .filter(skill -> !"rain_classroom_sync".equals(skill.name()))
                .filter(skill -> !"assignment_planner".equals(skill.name()))
                .toList();
    }

    public List<SkillDefinition> marketplace() {
        try {
            ClassPathResource resource = new ClassPathResource("skills/marketplace.json");
            if (!resource.exists()) {
                return List.of();
            }
            return objectMapper.readValue(resource.getInputStream(), new TypeReference<List<SkillDefinition>>() {
            });
        } catch (IOException ex) {
            throw new IllegalArgumentException("Skill 市场读取失败。", ex);
        }
    }

    public SkillDefinition importSkill(SkillDefinition skill, String signature) {
        SkillDefinition normalized = normalize(skill, skill.sourceUrl(), signature);
        upsert(normalized);
        save();
        return normalized;
    }

    public SkillDefinition importFromJson(String json, String signature) {
        try {
            SkillPackage skillPackage = objectMapper.readValue(json, SkillPackage.class);
            if (skillPackage.skill() != null) {
                String resolvedSignature = firstText(signature, skillPackage.signature());
                return importSkill(withSource(skillPackage.skill(), skillPackage.sourceUrl()), resolvedSignature);
            }
        } catch (IOException ignored) {
            // Fall through and try the direct SkillDefinition shape for backwards compatibility.
        }

        try {
            return importSkill(objectMapper.readValue(json, SkillDefinition.class), signature);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Skill JSON 格式不正确，请确认包含 name、title、description、triggerWords、enabled。", ex);
        }
    }

    public SkillDefinition downloadSkill(String sourceUrl) {
        assertAllowedSource(sourceUrl);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(sourceUrl))
                    .timeout(Duration.ofSeconds(12))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException("下载 Skill 失败，HTTP 状态码：" + response.statusCode());
            }
            SkillDefinition skill = importFromJson(response.body(), response.headers().firstValue("x-skill-signature").orElse(null));
            SkillDefinition withSource = normalize(skill, sourceUrl, skill.signature());
            upsert(withSource);
            save();
            return withSource;
        } catch (IOException ex) {
            throw new IllegalArgumentException("下载 Skill 失败，请检查 URL 是否可访问。", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("下载 Skill 已中断。", ex);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("下载 Skill 失败，请检查 URL 和 JSON 内容。", ex);
        }
    }

    public Map<String, Object> invocationHints(String skillName) {
        SkillDefinition skill = findSkill(skillName);
        if ("network_security_measure".equals(skill.name())) {
            return Map.of(
                    "skill", skill,
                    "prompt", "检测 https://example.com 的 HTTPS、TLS 证书、安全响应头和 DNS/TCP/TLS/TTFB 指标，并把结果整理成可执行的下一步。",
                    "parameters", skill.parameters() == null ? List.of() : skill.parameters()
            );
        }
        if ("web_app_listener".equals(skill.name())) {
            return Map.of(
                    "skill", skill,
                    "prompt", "开启 Web 请求监听，复盘最近捕获的请求，标注 Web 安全风险，并导出 Excel 表格。",
                    "parameters", skill.parameters() == null ? List.of() : skill.parameters()
            );
        }
        return Map.of(
                "skill", skill,
                "prompt", "请调用 " + skill.title() + " skill，并把结果整理成可执行的下一步。",
                "parameters", skill.parameters() == null ? List.of() : skill.parameters()
        );
    }

    public SkillDefinition findSkill(String skillName) {
        return skills.stream()
                .filter(item -> item.name().equals(skillName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到 Skill：" + skillName));
    }

    private void upsert(SkillDefinition skill) {
        skills.removeIf(item -> item.name().equals(skill.name()));
        skills.add(skill);
    }

    private void assertAllowedSource(String sourceUrl) {
        URI uri;
        try {
            uri = URI.create(sourceUrl);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Skill URL 格式不正确。", ex);
        }
        String host = uri.getHost();
        if (host == null || allowedHosts.stream().noneMatch(host::equalsIgnoreCase)) {
            throw new IllegalArgumentException("该 Skill 来源不在白名单中：" + host);
        }
    }

    private SkillDefinition normalize(SkillDefinition skill, String sourceUrl, String signature) {
        if (skill == null || blank(skill.name()) || blank(skill.title())) {
            throw new IllegalArgumentException("Skill 必须包含 name 和 title。");
        }
        List<SkillParameterDefinition> parameters = skill.parameters() == null
                ? List.of()
                : new ArrayList<>(skill.parameters());
        String resolvedSignature = firstText(signature, skill.signature());
        String signatureStatus = verifySignature(skill, resolvedSignature);
        return new SkillDefinition(
                skill.name().trim(),
                skill.title().trim(),
                blank(skill.description()) ? "用户导入的工作辅助 Skill。" : skill.description().trim(),
                skill.triggerWords() == null ? List.of(skill.title()) : new ArrayList<>(skill.triggerWords()),
                skill.enabled(),
                parameters,
                firstText(sourceUrl, skill.sourceUrl()),
                resolvedSignature,
                signatureStatus
        );
    }

    private String verifySignature(SkillDefinition skill, String signature) {
        if (signatureSecret.isBlank()) {
            return blank(signature) ? "unsigned" : "signature-not-checked";
        }
        if (blank(signature)) {
            throw new IllegalArgumentException("当前环境要求 Skill 签名，但该 Skill 未提供 signature。");
        }
        String expected = hmacSha256(canonicalPayload(skill), signatureSecret);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Skill 签名校验失败。");
        }
        return "verified";
    }

    private String canonicalPayload(SkillDefinition skill) {
        return skill.name() + "\n"
                + skill.title() + "\n"
                + (skill.description() == null ? "" : skill.description()) + "\n"
                + String.join(",", skill.triggerWords() == null ? List.of() : skill.triggerWords());
    }

    private String hmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Skill 签名计算失败。", ex);
        }
    }

    private List<SkillDefinition> loadStoredSkills() {
        if (!Files.exists(storagePath)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(storagePath.toFile(), new TypeReference<List<SkillDefinition>>() {
            });
        } catch (IOException ex) {
            throw new IllegalArgumentException("读取本地 Skill 持久化文件失败：" + storagePath, ex);
        }
    }

    private void save() {
        try {
            Files.createDirectories(storagePath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storagePath.toFile(), skills);
        } catch (IOException ex) {
            throw new IllegalArgumentException("保存本地 Skill 持久化文件失败：" + storagePath, ex);
        }
    }

    private List<SkillDefinition> mergeStoredSkills(List<SkillDefinition> builtIns, List<SkillDefinition> stored) {
        List<SkillDefinition> merged = new ArrayList<>(builtIns);
        for (SkillDefinition skill : stored) {
            merged.removeIf(item -> item.name().equals(skill.name()));
            merged.add(skill);
        }
        return merged;
    }

    private SkillDefinition withSource(SkillDefinition skill, String sourceUrl) {
        if (blank(sourceUrl)) {
            return skill;
        }
        return new SkillDefinition(
                skill.name(),
                skill.title(),
                skill.description(),
                skill.triggerWords(),
                skill.enabled(),
                skill.parameters(),
                sourceUrl,
                skill.signature(),
                skill.signatureStatus()
        );
    }

    private List<String> parseHosts(String value) {
        if (blank(value)) {
            return List.of();
        }
        return List.of(value.split(",")).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String firstText(String first, String second) {
        return blank(first) ? second : first;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private List<SkillDefinition> builtInSkills() {
        return List.of(
                new SkillDefinition(
                        "rain_classroom_sync",
                        "雨课堂同步",
                        "同步雨课堂等平台的作业、截止时间和提交状态。",
                        List.of("同步", "刷新", "雨课堂", "作业", "ddl", "DDL", "截止", "deadline"),
                        true,
                        List.of(
                                new SkillParameterDefinition("platform", "平台", "select", "", true, "雨课堂", List.of("雨课堂", "飞书录入", "全部")),
                                new SkillParameterDefinition("deadlineWindow", "截止范围", "select", "", false, "7天内", List.of("今天", "3天内", "7天内", "全部"))
                        ),
                        "",
                        "",
                        "built-in"
                ),
                new SkillDefinition(
                        "assignment_planner",
                        "学习计划",
                        "把作业拆解成今天、本周和截止前的执行计划。",
                        List.of("计划", "规划", "拆解", "今晚", "今天", "本周", "安排"),
                        true,
                        List.of(
                                new SkillParameterDefinition("availableHours", "可用时长", "number", "例如 2", true, "2", List.of()),
                                new SkillParameterDefinition("energyLevel", "精力状态", "select", "", false, "正常", List.of("低", "正常", "高"))
                        ),
                        "",
                        "",
                        "built-in"
                ),
                new SkillDefinition(
                        "llm_chat",
                        "模型问答",
                        "调用当前配置的大模型完成问答、总结和规划。",
                        List.of("问", "总结", "解释", "模型", "llm", "LLM", "复习"),
                        true,
                        List.of(
                                new SkillParameterDefinition("style", "输出风格", "select", "", false, "清晰简洁", List.of("清晰简洁", "详细解释", "行动清单"))
                        ),
                        "",
                        "",
                        "built-in"
                ),
                new SkillDefinition(
                        "network_security_measure",
                        "网络安全测量",
                        "检测 HTTPS、TLS 证书、安全响应头、Web 配置风险，并测量 DNS、TCP、TLS、TTFB 和总响应时间。",
                        List.of("检测", "测量", "网络", "安全头", "响应头", "TLS", "HTTPS", "http", "网站"),
                        true,
                        List.of(
                                new SkillParameterDefinition("url", "目标 URL", "text", "https://example.com", true, "", List.of()),
                                new SkillParameterDefinition("mode", "检测模式", "select", "", false, "综合检测", List.of("综合检测", "安全检测", "网络测量"))
                        ),
                        "",
                        "",
                        "built-in"
                ),
                new SkillDefinition(
                        "web_app_listener",
                        "Web Request Listener",
                        "Capture HTTP requests, inspect headers/body previews, identify web application risk hints, and export listener logs.",
                        List.of("listener", "listen", "webhook", "capture", "request", "header", "User-Agent", "监听", "请求", "Webhook"),
                        true,
                        List.of(
                                new SkillParameterDefinition("mode", "Mode", "select", "", false, "capture", List.of("capture", "review", "export")),
                                new SkillParameterDefinition("format", "Export format", "select", "", false, "xlsx", List.of("docx", "xlsx", "csv"))
                        ),
                        "",
                        "",
                        "built-in"
                ),
                new SkillDefinition(
                        "file_generate",
                        "文件生成",
                        "根据用户需求生成 Word、Excel、PDF、CSV、Markdown 或文本文件，并返回下载链接。",
                        List.of("文件", "文档", "表格", "PDF", "Word", "Excel", "docx", "xlsx", "csv", "导出", "生成"),
                        true,
                        List.of(
                                new SkillParameterDefinition("format", "文件格式", "select", "", true, "docx", List.of("docx", "xlsx", "pdf", "csv", "md", "txt")),
                                new SkillParameterDefinition("title", "文件标题", "text", "例如 作业清单", false, "agent-output", List.of())
                        ),
                        "",
                        "",
                        "built-in"
                ),
                new SkillDefinition(
                        "feishu_notify",
                        "飞书通知",
                        "通过飞书发送作业提醒和执行结果。",
                        List.of("飞书", "提醒", "通知", "发送"),
                        true,
                        List.of(
                                new SkillParameterDefinition("target", "通知对象", "text", "个人或群聊", false, "默认群聊", List.of()),
                                new SkillParameterDefinition("urgency", "提醒强度", "select", "", false, "普通", List.of("普通", "重要", "紧急"))
                        ),
                        "",
                        "",
                        "built-in"
                )
        );
    }
}
