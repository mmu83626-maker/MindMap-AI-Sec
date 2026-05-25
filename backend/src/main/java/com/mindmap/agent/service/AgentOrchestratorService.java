package com.mindmap.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindmap.academic.dto.AcademicChatRequest;
import com.mindmap.academic.dto.AcademicChatResponse;
import com.mindmap.academic.dto.AssignmentDto;
import com.mindmap.academic.dto.ManualAssignmentRequest;
import com.mindmap.academic.service.AcademicAgentService;
import com.mindmap.academic.service.AssignmentService;
import com.mindmap.agent.dto.AgentArtifact;
import com.mindmap.agent.dto.AgentCommandRequest;
import com.mindmap.agent.dto.AgentCommandResponse;
import com.mindmap.agent.dto.SkillDefinition;
import com.mindmap.llm.dto.LlmChatRequest;
import com.mindmap.llm.dto.LlmMessage;
import com.mindmap.llm.dto.LlmRuntimeSettings;
import com.mindmap.llm.service.LlmGatewayService;
import com.mindmap.llm.service.LlmSettingsService;
import com.mindmap.network.dto.NetworkMeasureReport;
import com.mindmap.network.dto.NetworkMeasureRequest;
import com.mindmap.network.dto.NetworkListenerEvent;
import com.mindmap.network.service.NetworkListenerService;
import com.mindmap.network.service.NetworkMeasureService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentOrchestratorService {

    private final SkillRegistryService skillRegistryService;
    private final AssignmentService assignmentService;
    private final AcademicAgentService academicAgentService;
    private final AgentEventLogService agentEventLogService;
    private final LlmSettingsService llmSettingsService;
    private final NetworkMeasureService networkMeasureService;
    private final NetworkListenerService networkListenerService;
    private final LlmGatewayService llmGatewayService;
    private final AgentArtifactService agentArtifactService;
    private final ObjectMapper objectMapper;

    public AgentOrchestratorService(
            SkillRegistryService skillRegistryService,
            AssignmentService assignmentService,
            AcademicAgentService academicAgentService,
            AgentEventLogService agentEventLogService,
            LlmSettingsService llmSettingsService,
            NetworkMeasureService networkMeasureService,
            NetworkListenerService networkListenerService,
            LlmGatewayService llmGatewayService,
            AgentArtifactService agentArtifactService,
            ObjectMapper objectMapper
    ) {
        this.skillRegistryService = skillRegistryService;
        this.assignmentService = assignmentService;
        this.academicAgentService = academicAgentService;
        this.agentEventLogService = agentEventLogService;
        this.llmSettingsService = llmSettingsService;
        this.networkMeasureService = networkMeasureService;
        this.networkListenerService = networkListenerService;
        this.llmGatewayService = llmGatewayService;
        this.agentArtifactService = agentArtifactService;
        this.objectMapper = objectMapper;
    }

    public AgentCommandResponse run(AgentCommandRequest request) {
        long started = System.currentTimeMillis();
        AgentCommandResponse response = execute(request);
        String runId = agentEventLogService.append(request, response).id();
        long duration = Math.max(1, System.currentTimeMillis() - started);
        for (String skillName : response.plannedSkills()) {
            findSkill(skillName).ifPresent(skill -> agentEventLogService.appendSkillExecution(
                    runId,
                    skill.name(),
                    skill.title(),
                    "success",
                    request.skillParameters(),
                    response.answer(),
                    duration
            ));
        }
        return response;
    }

    private AgentCommandResponse execute(AgentCommandRequest request) {
        String command = request.command() == null ? "" : request.command().trim();
        AgentPlan runtime = runtimePlan(command, request.context());
        AgentDecision decision = networkFocusedDecision(decide(command, request, runtime));
        Map<String, Object> data = baseData(runtime, decision, request);

        if (academicFeaturesEnabled(request)) {
        List<ManualAssignmentRequest> assignmentRequests = assignmentRequests(command, decision);
        if (!assignmentRequests.isEmpty()) {
            List<AssignmentDto> createdAssignments = new ArrayList<>();
            for (ManualAssignmentRequest assignmentRequest : assignmentRequests) {
                createdAssignments.add(assignmentService.addManualAssignment(assignmentRequest));
            }
            if (!createdAssignments.isEmpty()) {
                List<AssignmentDto> assignments = assignmentService.listAssignments();
                data.put("assignments", assignments);
                return new AgentCommandResponse(
                        formatAssignmentsCreated(createdAssignments, assignments, decision),
                        withoutSkill(decision.skills(), "network_security_measure"),
                        data,
                        OffsetDateTime.now()
                );
            }
        }

        if (decision.skills().contains("rain_classroom_sync") && isSyncOnly(command)) {
            List<AssignmentDto> assignments = assignmentService.listAssignments();
            data.put("assignments", assignments);
            return new AgentCommandResponse("已读取作业。\n\n" + assignmentDigest(assignments), decision.skills(), data, OffsetDateTime.now());
        }

        }

        String networkTarget = firstNonBlank(decision.targetUrl(), extractUrl(command), valueFromParameters(request.skillParameters(), "url"));
        if (decision.skills().contains("network_security_measure") && networkTarget != null) {
            NetworkMeasureReport report = networkMeasureService.measure(new NetworkMeasureRequest(
                    networkTarget,
                    List.of("dns", "tcp", "tls", "headers", "http"),
                    1
            ));
            data.put("networkReport", report);
            return new AgentCommandResponse(formatNetworkReport(report), decision.skills(), data, OffsetDateTime.now());
        }
        if (decision.skills().contains("network_security_measure")) {
            return new AgentCommandResponse("网络安全测量需要目标 URL。请直接发：检测 https://example.com", decision.skills(), data, OffsetDateTime.now());
        }

        if (decision.skills().contains("web_app_listener")) {
            List<NetworkListenerEvent> events = networkListenerService.events(20);
            data.put("listenerStatus", networkListenerService.status("http://localhost:8090"));
            data.put("listenerEvents", events);
            if (decision.artifact() != null) {
                String format = firstNonBlank(decision.artifact().format(), "xlsx");
                AgentArtifact artifact = agentArtifactService.create(format, "web-listener-events", listenerArtifactContent(events));
                data.put("artifacts", List.of(artifact));
                return new AgentCommandResponse(
                        "Web 监听已就绪：/api/network/listener/capture\n已导出监听日志：" + artifact.filename() + "\n下载链接：" + artifact.url(),
                        withSkill(decision.skills(), "file_generate"),
                        data,
                        OffsetDateTime.now()
                );
            }
            return new AgentCommandResponse(
                    "Web 监听已就绪：/api/network/listener/capture\n最近捕获 " + events.size() + " 条请求。可以把 Webhook 或测试请求打到这个地址，然后导出 Word/Excel 分析。",
                    decision.skills(),
                    data,
                    OffsetDateTime.now()
            );
        }

        if (decision.artifact() != null) {
            String content = generateArtifactContent(command, runtime, decision);
            AgentArtifact artifact = agentArtifactService.create(decision.artifact().format(), decision.artifact().title(), content);
            data.put("artifacts", List.of(artifact));
            return new AgentCommandResponse(
                    "已生成文件：" + artifact.filename() + "\n下载链接：" + artifact.url(),
                    withSkill(decision.skills(), "file_generate"),
                    data,
                    OffsetDateTime.now()
            );
        }

        AcademicChatResponse chat = academicAgentService.chat(new AcademicChatRequest(
                buildDelegatedPrompt(command, runtime, decision),
                request.channel(),
                null,
                runtime.provider(),
                runtime.model(),
                runtime.baseUrl(),
                firstNonBlank(valueFromContext(request.context(), "apiKey"), runtime.apiKey()),
                runtime.apiPath(),
                runtime.authType()
        ));
        data.put("relatedAssignments", chat.relatedAssignments());
        return new AgentCommandResponse(chat.answer(), decision.skills(), data, OffsetDateTime.now());
    }

    private Map<String, Object> baseData(AgentPlan runtime, AgentDecision decision, AgentCommandRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> agentPlan = new LinkedHashMap<>();
        agentPlan.put("provider", firstNonBlank(runtime.provider(), ""));
        agentPlan.put("model", firstNonBlank(runtime.model(), ""));
        agentPlan.put("baseUrl", firstNonBlank(runtime.baseUrl(), ""));
        agentPlan.put("apiPath", firstNonBlank(runtime.apiPath(), ""));
        agentPlan.put("authType", firstNonBlank(runtime.authType(), ""));
        agentPlan.put("skills", decision.skills());
        agentPlan.put("configured", !isBlank(runtime.apiKey()));
        data.put("agentPlan", agentPlan);

        Map<String, Object> llmPlanner = new LinkedHashMap<>();
        llmPlanner.put("used", decision.usedLlm());
        llmPlanner.put("intent", firstNonBlank(decision.intent(), "unknown"));
        llmPlanner.put("raw", firstNonBlank(decision.rawPlan(), ""));
        data.put("llmPlanner", llmPlanner);
        data.put("skillParameters", request.skillParameters() == null ? Map.of() : request.skillParameters());
        return data;
    }

    private boolean academicFeaturesEnabled(AgentCommandRequest request) {
        return "true".equalsIgnoreCase(valueFromContext(request.context(), "enableAcademicFeatures"));
    }

    private AgentDecision networkFocusedDecision(AgentDecision decision) {
        List<String> skills = new ArrayList<>(decision.skills());
        skills.remove("rain_classroom_sync");
        skills.remove("assignment_planner");
        if (skills.isEmpty()) {
            skills.add("llm_chat");
        }
        return new AgentDecision(
                decision.intent(),
                List.copyOf(skills),
                null,
                List.of(),
                decision.artifact(),
                decision.targetUrl(),
                decision.usedLlm(),
                decision.rawPlan()
        );
    }

    private AgentDecision decide(String command, AgentCommandRequest request, AgentPlan runtime) {
        if (!isBlank(runtime.apiKey())) {
            try {
                AgentDecision decision = decideWithLlm(command, request, runtime);
                if (!decision.skills().isEmpty()) {
                    return decision;
                }
            } catch (Exception ignored) {
                // Fall back to deterministic routing when the model is unavailable or returns malformed JSON.
            }
        }
        return fallbackDecision(command, request, runtime);
    }

    private AgentDecision decideWithLlm(String command, AgentCommandRequest request, AgentPlan runtime) throws Exception {
        String raw = llmGatewayService.chat(new LlmChatRequest(
                runtime.provider(),
                runtime.model(),
                runtime.baseUrl(),
                null,
                List.of(
                        new LlmMessage("system", plannerSystemPrompt()),
                        new LlmMessage("user", plannerUserPrompt(command, request, runtime))
                ),
                0.1,
                1200,
                firstNonBlank(valueFromContext(request.context(), "apiKey"), runtime.apiKey()),
                runtime.apiPath(),
                runtime.authType()
        )).content();
        JsonNode root = objectMapper.readTree(extractJson(raw));
        List<String> skills = readSkills(root.path("skills"));
        if (skills.isEmpty()) {
            skills = runtime.skills();
        }
        ManualAssignmentRequest assignment = readAssignment(root.path("assignment"));
        List<ManualAssignmentRequest> assignments = readAssignments(root.path("assignments"));
        ArtifactRequest artifact = readArtifact(root.path("artifact"));
        String targetUrl = text(root, "targetUrl");
        String intent = text(root, "intent");
        return new AgentDecision(intent, skills, assignment, assignments, artifact, targetUrl, true, raw);
    }

    private String plannerSystemPrompt() {
        return """
                你是 NetScope AI 的调度模型。你的任务不是聊天，而是把用户自然语言转成可执行 JSON。
                只能输出 JSON，不要 Markdown，不要解释。
                可用 skills:
                - feishu_notify: 飞书提醒、通知
                - network_security_measure: 网站/URL 的 HTTPS、TLS、安全头、网络测量
                - web_app_listener: Web 请求监听、Header/Body 复盘、监听日志导出
                - llm_chat: 普通问答、总结、解释、改写
                - file_generate: 生成 docx/xlsx/pdf/csv/md/txt 文件
                JSON schema:
                {
                  "intent": "network_measure|web_listener|file_generate|chat|notify",
                  "skills": ["network_security_measure"],
                  "targetUrl": "",
                  "artifact": {
                    "format": "docx|xlsx|pdf|csv|md|txt",
                    "title": "",
                    "contentHint": ""
                  }
                }
                规则：
                1. 用户要 Word/Excel/PDF/表格/文档/文件时，加入 file_generate 并填写 artifact。
                2. 网络检测在出现 URL、网站检测、TLS、HTTPS、安全响应头、网络测量时选 network_security_measure。
                3. 用户提到监听、Webhook、请求日志、Header、Body、User-Agent 时选 web_app_listener。
                4. 字段不确定时尽量根据上下文补齐，不要要求固定格式。
                """;
    }

    private String plannerUserPrompt(String command, AgentCommandRequest request, AgentPlan runtime) {
        return """
                当前时间：%s
                当前渠道：%s
                前端显式 skillName：%s
                规则兜底 skills：%s
                用户原文：
                %s
                """.formatted(
                OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                firstNonBlank(request.channel(), "web"),
                valueFromContext(request.context(), "skillName"),
                runtime.skills(),
                command
        );
    }

    private AgentDecision fallbackDecision(String command, AgentCommandRequest request, AgentPlan runtime) {
        List<String> skills = new ArrayList<>(runtime.skills());
        ManualAssignmentRequest assignment = isAssignmentIntake(command) ? parseAssignment(command) : null;
        List<ManualAssignmentRequest> assignments = isAssignmentIntake(command) ? parseAssignments(command) : List.of();
        ArtifactRequest artifact = parseArtifact(command);
        String targetUrl = extractUrl(command);
        if (assignment != null || !assignments.isEmpty()) {
            skills.remove("network_security_measure");
            addSkill(skills, "assignment_planner");
        }
        if (artifact != null) {
            addSkill(skills, "file_generate");
        }
        skills.remove("rain_classroom_sync");
        skills.remove("assignment_planner");
        return new AgentDecision("fallback", List.copyOf(skills), assignment, assignments, artifact, targetUrl, false, "");
    }

    private AgentPlan runtimePlan(String command, Map<String, Object> context) {
        LlmRuntimeSettings runtime = llmSettingsService.current();
        String requestedSkill = valueFromContext(context, "skillName");
        String provider = firstNonBlank(valueFromContext(context, "llmProvider"), providerFromCommand(command), runtime.provider());
        String model = firstNonBlank(valueFromContext(context, "llmModel"), runtime.model());
        String baseUrl = firstNonBlank(valueFromContext(context, "llmBaseUrl"), runtime.baseUrl());
        String apiKey = firstNonBlank(valueFromContext(context, "apiKey"), runtime.apiKey());
        String apiPath = firstNonBlank(valueFromContext(context, "apiPath"), runtime.apiPath());
        String authType = firstNonBlank(valueFromContext(context, "authType"), runtime.authType());

        List<String> skills = new ArrayList<>();
        String lower = command.toLowerCase(Locale.ROOT);
        for (SkillDefinition skill : skillRegistryService.listSkills()) {
            if (skill.enabled() && skill.triggerWords() != null
                    && skill.triggerWords().stream().anyMatch(word -> lower.contains(word.toLowerCase(Locale.ROOT)))) {
                addSkill(skills, skill.name());
            }
        }
        if (!isBlank(requestedSkill) && !skills.contains(requestedSkill) && !hasStrongAutoSkill(command, skills)) {
            addSkill(skills, requestedSkill);
        }
        if (skills.isEmpty()) {
            addSkill(skills, "llm_chat");
        }
        if (containsAny(command, "规划", "计划", "今晚", "拆解", "完成")) {
            addSkill(skills, "assignment_planner");
        }
        if (containsAny(command, "提醒", "通知", "发到飞书")) {
            addSkill(skills, "feishu_notify");
        }
        if ((containsAny(command, "检测", "测量", "网络", "安全头", "响应头", "TLS", "HTTPS", "http") || extractUrl(command) != null)) {
            addSkill(skills, "network_security_measure");
        }
        if (containsAny(command, "监听", "Webhook", "webhook", "请求日志", "Header", "Body", "User-Agent", "抓包")) {
            addSkill(skills, "web_app_listener");
        }
        if (isAssignmentIntake(command) && extractUrl(command) == null) {
            skills.remove("network_security_measure");
            addSkill(skills, "assignment_planner");
        }
        if (parseArtifact(command) != null) {
            addSkill(skills, "file_generate");
        }
        skills.remove("rain_classroom_sync");
        skills.remove("assignment_planner");
        return new AgentPlan(provider, model, baseUrl, apiKey, apiPath, authType, List.copyOf(skills));
    }

    private boolean hasStrongAutoSkill(String command, List<String> skills) {
        if (skills.contains("network_security_measure") && (extractUrl(command) != null
                || containsAny(command, "检测", "测量", "网络", "安全头", "响应头", "TLS", "HTTPS", "http"))) {
            return true;
        }
        if (skills.contains("feishu_notify") && containsAny(command, "飞书", "通知", "提醒")) {
            return true;
        }
        return skills.contains("web_app_listener") && containsAny(command, "监听", "Webhook", "webhook", "请求日志", "Header", "Body", "User-Agent", "抓包");
    }

    private ManualAssignmentRequest readAssignment(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String title = text(node, "title");
        String deadline = text(node, "deadline");
        if (isBlank(title) || isBlank(deadline)) {
            return null;
        }
        return new ManualAssignmentRequest(
                firstNonBlank(text(node, "course"), "手动录入"),
                title,
                firstNonBlank(text(node, "status"), "待完成"),
                normalizeLlmDeadline(deadline),
                node.path("timed").asBoolean(false),
                node.path("timeLimitMinutes").isNumber() ? node.path("timeLimitMinutes").asInt() : null,
                text(node, "note")
        );
    }

    private List<ManualAssignmentRequest> readAssignments(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<ManualAssignmentRequest> assignments = new ArrayList<>();
        for (JsonNode item : node) {
            ManualAssignmentRequest assignment = readAssignment(item);
            if (assignment != null) {
                assignments.add(assignment);
            }
        }
        return List.copyOf(assignments);
    }

    private ArtifactRequest readArtifact(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String format = text(node, "format");
        if (isBlank(format)) {
            return null;
        }
        return new ArtifactRequest(format, firstNonBlank(text(node, "title"), "agent-output"), text(node, "contentHint"));
    }

    private List<String> readSkills(JsonNode node) {
        List<String> skills = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText("");
                if (!value.isBlank()) {
                    addSkill(skills, value);
                }
            }
        }
        return List.copyOf(skills);
    }

    private String generateArtifactContent(String command, AgentPlan runtime, AgentDecision decision) {
        try {
            return llmGatewayService.chat(new LlmChatRequest(
                    runtime.provider(),
                    runtime.model(),
                    runtime.baseUrl(),
                    null,
                    List.of(
                            new LlmMessage("system", "你负责生成文件正文。直接输出正文内容，不要解释。Excel/CSV 优先输出表格型内容；Word/PDF 输出结构化正文。"),
                            new LlmMessage("user", "用户需求：\n" + command + "\n\n文件要求："
                                    + decision.artifact().format() + " / " + decision.artifact().title()
                                    + "\n补充提示：" + decision.artifact().contentHint())
                    ),
                    0.4,
                    2500,
                    runtime.apiKey(),
                    runtime.apiPath(),
                    runtime.authType()
            )).content();
        } catch (Exception ex) {
            return command;
        }
    }

    private String listenerArtifactContent(List<NetworkListenerEvent> events) {
        StringBuilder content = new StringBuilder();
        content.append("Captured At\tMethod\tPath\tQuery\tSource IP\tUser-Agent\tContent-Type\tBody Preview\tRisk Hints\n");
        for (NetworkListenerEvent event : events) {
            content.append(cleanCell(event.capturedAt().toString())).append('\t')
                    .append(cleanCell(event.method())).append('\t')
                    .append(cleanCell(event.path())).append('\t')
                    .append(cleanCell(event.queryString())).append('\t')
                    .append(cleanCell(event.sourceIp())).append('\t')
                    .append(cleanCell(event.userAgent())).append('\t')
                    .append(cleanCell(event.contentType())).append('\t')
                    .append(cleanCell(event.bodyPreview())).append('\t')
                    .append(cleanCell(String.join("; ", event.riskHints()))).append('\n');
        }
        if (events.isEmpty()) {
            content.append("No events captured yet.\n");
        }
        return content.toString();
    }

    private String cleanCell(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }

    private String buildDelegatedPrompt(String command, AgentPlan plan, AgentDecision decision) {
        return """
                用户通过网页或机器人发来任务。你是 NetScope AI 计算机网络 Agent 的执行模型。
                你的重点是网络安全、网络测量、Web 应用监听、HTTPS/TLS、安全响应头、性能瓶颈定位和报告生成。
                LLM 调度意图：%s
                计划调用能力：%s
                用户原文：
                %s

                请直接给出可执行结果。不要声称“我会去调用工具”；已经能完成的就完成。
                不要主动输出作业、课程、截止时间、复习计划或学习规划内容。
                """.formatted(decision.intent(), String.join(", ", decision.skills()), command);
    }

    private boolean isAssignmentIntake(String command) {
        return containsAny(command, "录入作业", "添加作业", "新增作业", "截止时间", "截止:")
                || (containsAny(command, "作业", "考试", "实验", "测验") && containsAny(command, "截止", "deadline", "DDL", "ddl"));
    }

    private AssignmentDto tryCreateAssignment(String command) {
        ManualAssignmentRequest request = parseAssignment(command);
        return request == null ? null : assignmentService.addManualAssignment(request);
    }

    private List<ManualAssignmentRequest> assignmentRequests(String command, AgentDecision decision) {
        List<ManualAssignmentRequest> parsedAssignments = isAssignmentIntake(command) ? parseAssignments(command) : List.of();
        if (parsedAssignments.size() > 1) {
            return dedupeAssignments(parsedAssignments);
        }

        List<ManualAssignmentRequest> requests = new ArrayList<>();
        if (decision.assignment() != null) {
            requests.add(decision.assignment());
        }
        if (decision.assignments() != null) {
            requests.addAll(decision.assignments());
        }
        requests.addAll(parsedAssignments);
        return dedupeAssignments(requests);
    }

    private List<ManualAssignmentRequest> dedupeAssignments(List<ManualAssignmentRequest> requests) {
        List<ManualAssignmentRequest> deduped = new ArrayList<>();
        for (ManualAssignmentRequest request : requests) {
            if (request == null || isBlank(request.title()) || isBlank(request.deadline())) {
                continue;
            }
            boolean exists = deduped.stream().anyMatch(item ->
                    item.title().equalsIgnoreCase(request.title())
                            && item.deadline().equalsIgnoreCase(request.deadline()));
            if (!exists) {
                deduped.add(request);
            }
        }
        return List.copyOf(deduped);
    }

    private List<ManualAssignmentRequest> parseAssignments(String command) {
        List<ManualAssignmentRequest> assignments = new ArrayList<>();
        Matcher matcher = Pattern.compile("(20\\d{2}-\\d{1,2}-\\d{1,2})\\s*[/ T]\\s*(\\d{1,2}:\\d{2})").matcher(command);
        List<DeadlineMatch> deadlines = new ArrayList<>();
        while (matcher.find()) {
            deadlines.add(new DeadlineMatch(matcher.start(), matcher.end(), matcher.group(1), matcher.group(2)));
        }
        for (int i = 0; i < deadlines.size(); i++) {
            DeadlineMatch current = deadlines.get(i);
            int previousEnd = i == 0 ? 0 : deadlines.get(i - 1).end();
            int nextStart = i + 1 < deadlines.size() ? deadlines.get(i + 1).start() : command.length();
            String before = command.substring(previousEnd, current.start());
            String after = command.substring(current.end(), nextStart);
            String title = firstNonBlank(lastAssignmentTitle(before), firstAssignmentTitle(after), extractAssignmentTitle(before + "\n" + after));
            if (isBlank(title)) {
                continue;
            }
            assignments.add(new ManualAssignmentRequest(
                    extractCourse(title),
                    title,
                    "待完成",
                    normalizeDeadline(current.date(), current.time()),
                    false,
                    null,
                    compactNote((before + " " + after).trim())
            ));
        }
        if (assignments.isEmpty()) {
            ManualAssignmentRequest single = parseAssignment(command);
            if (single != null) {
                assignments.add(single);
            }
        }
        return List.copyOf(assignments);
    }

    private ManualAssignmentRequest parseAssignment(String command) {
        Matcher deadline = Pattern.compile("(20\\d{2}-\\d{1,2}-\\d{1,2})\\s*[/ T]\\s*(\\d{1,2}:\\d{2})").matcher(command);
        if (!deadline.find()) {
            return null;
        }
        String title = extractAssignmentTitle(command);
        if (isBlank(title)) {
            return null;
        }
        Integer minutes = extractMinutes(command);
        return new ManualAssignmentRequest(
                extractCourse(command),
                title,
                "待完成",
                normalizeDeadline(deadline.group(1), deadline.group(2)),
                minutes != null,
                minutes,
                compactNote(command)
        );
    }

    private ArtifactRequest parseArtifact(String command) {
        if (!containsAny(command, "生成", "导出", "文件", "文档", "表格", "PDF", "pdf", "Word", "Excel", "docx", "xlsx")) {
            return null;
        }
        String format = "txt";
        if (containsAny(command, "PDF", "pdf")) format = "pdf";
        else if (containsAny(command, "Excel", "excel", "表格", "xlsx")) format = "xlsx";
        else if (containsAny(command, "Word", "word", "文档", "docx")) format = "docx";
        else if (containsAny(command, "csv", "CSV")) format = "csv";
        return new ArtifactRequest(format, "agent-output", command);
    }

    private String extractAssignmentTitle(String command) {
        String fallback = "";
        for (String rawLine : command.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.contains("截止") || line.contains("满分") || line.matches(".*\\d+\\s*题.*")) {
                continue;
            }
            if (containsAny(line, "实验", "作业", "测验", "考试", "报告")) {
                String title = stripAssignmentPrefix(line);
                if (title.equals("考试") || title.equals("作业") || title.equals("测验")) {
                    fallback = title;
                    continue;
                }
                return title;
            }
        }
        return fallback;
    }

    private String firstAssignmentTitle(String text) {
        for (String rawLine : text.split("\\R")) {
            String title = normalizeAssignmentTitle(rawLine);
            if (!isBlank(title)) {
                return title;
            }
        }
        return "";
    }

    private String lastAssignmentTitle(String text) {
        String title = "";
        for (String rawLine : text.split("\\R")) {
            String candidate = normalizeAssignmentTitle(rawLine);
            if (!isBlank(candidate)) {
                title = candidate;
            }
        }
        return title;
    }

    private String normalizeAssignmentTitle(String rawLine) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (line.isEmpty()
                || line.contains("截止")
                || line.contains("满分")
                || line.contains("最近作业")
                || line.contains("规划")
                || line.contains("学习顺序")
                || line.contains("录入作业")
                || line.contains("共")
                || line.equals("考试")
                || line.equals("作业")
                || line.equals("测验")) {
            return "";
        }
        if (!containsAny(line, "实验", "作业", "测验", "考试", "报告", "协议", "加密", "证书", "DNS", "HTTP", "电子邮件")) {
            return "";
        }
        return stripAssignmentPrefix(line);
    }

    private String extractCourse(String command) {
        for (String rawLine : command.split("\\R")) {
            String line = rawLine.trim();
            if (line.length() >= 3 && line.length() <= 24
                    && containsAny(line, "课", "学", "安全", "编程", "英语")
                    && !line.contains("截止")) {
                return line;
            }
        }
        return "手动录入";
    }

    private Integer extractMinutes(String command) {
        Matcher matcher = Pattern.compile("限时\\s*(\\d{1,3})").matcher(command);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private String normalizeLlmDeadline(String deadline) {
        if (deadline.matches("20\\d{2}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}")) {
            return deadline;
        }
        if (deadline.matches("20\\d{2}-\\d{2}-\\d{2}T\\d{2}:\\d{2}")) {
            return deadline + ":00+08:00";
        }
        if (deadline.matches("20\\d{2}-\\d{1,2}-\\d{1,2}[/ T]\\d{1,2}:\\d{2}")) {
            String[] parts = deadline.replace("/", " ").replace("T", " ").split("\\s+");
            return normalizeDeadline(parts[0], parts[1]);
        }
        return deadline;
    }

    private String normalizeDeadline(String date, String time) {
        String[] dateParts = date.split("-");
        String[] timeParts = time.split(":");
        return "%04d-%02d-%02dT%02d:%02d:00+08:00".formatted(
                Integer.parseInt(dateParts[0]),
                Integer.parseInt(dateParts[1]),
                Integer.parseInt(dateParts[2]),
                Integer.parseInt(timeParts[0]),
                Integer.parseInt(timeParts[1])
        );
    }

    private String compactNote(String command) {
        String singleLine = command.replaceAll("\\s+", " ").trim();
        return singleLine.length() > 180 ? singleLine.substring(0, 180) + "..." : singleLine;
    }

    private String stripAssignmentPrefix(String title) {
        return title.replaceFirst("^录入作业\\s*", "").replaceFirst("^添加作业\\s*", "").trim();
    }

    private String formatAssignmentCreated(AssignmentDto assignment, List<AssignmentDto> assignments, AgentDecision decision) {
        String via = decision.usedLlm() ? "DeepSeek 已分析并录入作业：" : "已录入作业：";
        return via + "\n"
                + "- 课程：" + assignment.course() + "\n"
                + "- 作业：" + assignment.title() + "\n"
                + "- 截止：" + assignment.deadline().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) + "\n\n"
                + assignmentDigest(assignments);
    }

    private String formatAssignmentsCreated(List<AssignmentDto> createdAssignments, List<AssignmentDto> assignments, AgentDecision decision) {
        if (createdAssignments.size() == 1) {
            return formatAssignmentCreated(createdAssignments.get(0), assignments, decision);
        }
        StringBuilder builder = new StringBuilder(decision.usedLlm() ? "DeepSeek 已分析并批量录入作业：" : "已批量录入作业：");
        for (AssignmentDto assignment : createdAssignments) {
            builder.append("\n- ")
                    .append(assignment.title())
                    .append("，截止 ")
                    .append(assignment.deadline().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")));
        }
        builder.append("\n\n今晚建议顺序：");
        List<AssignmentDto> sortedCreated = new ArrayList<>(createdAssignments);
        sortedCreated.sort(java.util.Comparator.comparing(AssignmentDto::deadline));
        int index = 1;
        for (AssignmentDto assignment : sortedCreated) {
            builder.append("\n")
                    .append(index++)
                    .append(". ")
                    .append(assignment.title())
                    .append("（截止 ")
                    .append(assignment.deadline().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")))
                    .append("）");
        }
        builder.append("\n\n").append(assignmentDigest(assignments));
        return builder.toString();
    }

    private String formatNetworkReport(NetworkMeasureReport report) {
        return """
                已完成网络安全测量。
                目标：%s
                风险等级：%s
                HTTPS：%s
                证书：%s
                DNS：%s ms
                TCP：%s ms
                TLS：%s
                TTFB：%s

                %s
                """.formatted(
                report.target(),
                report.riskLevel(),
                report.httpsEnabled() ? "已启用" : "未启用",
                report.certificateValid() ? "有效" : "异常",
                value(report.dnsMs()),
                value(report.tcpMs()),
                report.tlsMs() == null ? "n/a" : report.tlsMs() + " ms",
                report.ttfbMs() == null ? "n/a" : report.ttfbMs() + " ms",
                report.summary()
        );
    }

    private String assignmentDigest(List<AssignmentDto> assignments) {
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
                    .append(assignment.deadline().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")))
                    .append("，状态：")
                    .append(assignment.status());
        }
        return builder.toString();
    }

    private boolean isSyncOnly(String command) {
        return containsAny(command, "同步", "刷新", "读取", "查看")
                && !containsAny(command, "规划", "计划", "总结", "分析", "怎么", "如何", "提醒");
    }

    private String extractUrl(String command) {
        if (command == null) return null;
        Matcher matcher = Pattern.compile("(https?://[^\\s，。；,;]+|(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(?:/[^\\s，。；,;]*)?)").matcher(command);
        if (!matcher.find()) return null;
        String target = matcher.group(1);
        return target.startsWith("http://") || target.startsWith("https://") ? target : "https://" + target;
    }

    private String extractJson(String raw) {
        String text = raw == null ? "" : raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String providerFromCommand(String command) {
        if (containsAny(command, "kimi", "moonshot", "月之暗面")) return "kimi";
        if (containsAny(command, "豆包", "doubao", "ark", "火山")) return "doubao";
        if (containsAny(command, "openai", "gpt")) return "openai";
        if (containsAny(command, "deepseek", "DeepSeek")) return "custom";
        return null;
    }

    private java.util.Optional<SkillDefinition> findSkill(String skillName) {
        return skillRegistryService.listSkills().stream().filter(item -> item.name().equals(skillName)).findFirst();
    }

    private List<String> withoutSkill(List<String> skills, String skillName) {
        return skills.stream().filter(skill -> !skillName.equals(skill)).toList();
    }

    private List<String> withSkill(List<String> skills, String skillName) {
        List<String> next = new ArrayList<>(skills);
        addSkill(next, skillName);
        return List.copyOf(next);
    }

    private void addSkill(List<String> skills, String skillName) {
        if (!isBlank(skillName) && !skills.contains(skillName)) {
            skills.add(skillName);
        }
    }

    private String value(Long value) {
        return value == null ? "n/a" : String.valueOf(value);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        return value == null || value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private String valueFromContext(Map<String, Object> context, String key) {
        if (context == null || !context.containsKey(key) || context.get(key) == null) return null;
        return String.valueOf(context.get(key));
    }

    private String valueFromParameters(Map<String, Object> parameters, String key) {
        if (parameters == null || !parameters.containsKey(key) || parameters.get(key) == null) return null;
        return String.valueOf(parameters.get(key));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) return value;
        }
        return null;
    }

    private boolean containsAny(String text, String... keywords) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record AgentPlan(
            String provider,
            String model,
            String baseUrl,
            String apiKey,
            String apiPath,
            String authType,
            List<String> skills
    ) {
    }

    private record AgentDecision(
            String intent,
            List<String> skills,
            ManualAssignmentRequest assignment,
            List<ManualAssignmentRequest> assignments,
            ArtifactRequest artifact,
            String targetUrl,
            boolean usedLlm,
            String rawPlan
    ) {
    }

    private record ArtifactRequest(
            String format,
            String title,
            String contentHint
    ) {
    }

    private record DeadlineMatch(
            int start,
            int end,
            String date,
            String time
    ) {
    }
}

