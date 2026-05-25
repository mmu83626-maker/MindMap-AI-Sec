package com.mindmap.academic.service;

import com.mindmap.academic.dto.AcademicChatRequest;
import com.mindmap.academic.dto.AcademicChatResponse;
import com.mindmap.academic.dto.AssignmentDto;
import com.mindmap.llm.dto.LlmChatRequest;
import com.mindmap.llm.dto.LlmChatResponse;
import com.mindmap.llm.dto.LlmMessage;
import com.mindmap.llm.service.LlmGatewayService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AcademicAgentService {

    private final AssignmentService assignmentService;
    private final LlmGatewayService llmGatewayService;

    public AcademicAgentService(AssignmentService assignmentService, LlmGatewayService llmGatewayService) {
        this.assignmentService = assignmentService;
        this.llmGatewayService = llmGatewayService;
    }

    public AcademicChatResponse chat(AcademicChatRequest request) {
        String message = request.message() == null ? "" : request.message().trim();
        List<AssignmentDto> upcoming = List.of();
        List<String> actions = List.of("识别网络任务", "调用大模型执行分析", "返回网页/飞书结果");

        String answer = buildLlmAnswer(request, message, upcoming);
        return new AcademicChatResponse(answer, actions, upcoming, OffsetDateTime.now());
    }

    private String buildLlmAnswer(AcademicChatRequest request, String message, List<AssignmentDto> assignments) {
        if (message.isBlank()) {
            return buildRuleAnswer(message, assignments);
        }

        List<LlmMessage> messages = List.of(
                new LlmMessage(
                        "system",
                        """
                        你是 NetScope AI 的计算机网络工作代理，角色类似 OpenClaw 中的任务执行 Agent。
                        你会接收来自飞书或网页的自然语言任务，重点处理网络安全、网络测量、Web 应用监听、HTTPS/TLS、安全响应头和报告生成。

                        工作要求：
                        - 先理解用户意图，再执行任务。
                        - 如果用户要检测网站，围绕 DNS、TCP、TLS、TTFB、HTTP 状态和安全响应头组织答案。
                        - 如果用户要监听或 Webhook，说明监听地址、请求日志字段和风险提示。
                        - 如果用户要报告或表格，说明可生成 Word、Excel、CSV 或 PDF。
                        - 不要声称已经访问外部平台，除非上下文中确实提供了结果。
                        - 不要主动输出作业、课程、截止时间、复习计划或学习规划内容。
                        - 输出适合飞书阅读，简洁、具体、中文优先。
                        """
                ),
                new LlmMessage(
                        "user",
                        "当前可用能力：网络安全测量、实时网络流程可视化、网站安全体检卡、网站对标比较、Web 请求监听、Word/Excel 报告生成。\n\n用户任务：\n" + message
                )
        );

        try {
            LlmChatResponse response = llmGatewayService.chat(new LlmChatRequest(
                    request.llmProvider(),
                    request.llmModel(),
                    request.llmBaseUrl(),
                    null,
                    messages,
                    0.4,
                    1400,
                    request.apiKey(),
                    request.apiPath(),
                    request.authType()
            ));
            return response.content();
        } catch (ResponseStatusException ex) {
            return buildRuleAnswer(message, assignments)
                    + "\n\n提示：当前大模型未完成调用，原因是：" + safeReason(ex)
                    + "\n请在网页「模型设置」里保存可用的 Model URL、接口路径、API Key、模型名和鉴权方式。";
        }
    }

    private String buildAssignmentContext(List<AssignmentDto> assignments) {
        if (assignments.isEmpty()) {
            return "暂无作业。";
        }

        StringBuilder builder = new StringBuilder();
        for (AssignmentDto assignment : assignments) {
            builder.append("- ")
                    .append(assignment.course())
                    .append(" / ")
                    .append(assignment.title())
                    .append(" / ")
                    .append(assignment.status())
                    .append(" / 截止 ")
                    .append(assignment.deadline().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                    .append(assignment.timed() ? " / 限时 " + assignment.timeLimitMinutes() + " 分钟" : "")
                    .append(blank(assignment.note()) ? "" : " / 备注：" + assignment.note())
                    .append("\n");
        }
        return builder.toString();
    }

    private String buildRuleAnswer(String message, List<AssignmentDto> assignments) {
        if (message.isBlank() || containsAny(message, "帮助", "help", "菜单")) {
            return """
                    NetScope AI 网络 Agent 已连接。
                    我可以通过网页或飞书处理这些网络任务：
                    - 检测 https://example.com 的 HTTPS、TLS 证书和安全响应头
                    - 测量 DNS/TCP/TLS/TTFB 并定位性能瓶颈
                    - 开启 Web 请求监听并分析 Header、Body、User-Agent
                    - 对比两个网站的安全性和性能
                    - 生成 Word / Excel / CSV / PDF 分析报告
                    """;
        }

        StringBuilder builder = new StringBuilder();
        if (containsAny(message, "检测", "网站", "https", "http", "tls", "证书", "安全头", "响应头", "dns", "tcp", "ttfb")) {
            builder.append("可以执行网站网络安全测量。请直接发送目标 URL，例如：检测 https://example.com 的 HTTPS、TLS 证书、安全响应头和 DNS/TCP/TLS/TTFB 指标。");
        }

        if (containsAny(message, "监听", "webhook", "请求", "header", "body", "user-agent")) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append("Web 请求监听已支持。可向 /api/network/listener/capture 发送 GET/POST 请求，然后导出 Word 或 Excel 日志。");
        }

        if (containsAny(message, "报告", "word", "excel", "xlsx", "docx", "pdf", "表格", "导出")) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append("可以生成网络测量报告、监听日志或网站对标报告，支持 Word、Excel、CSV 和 PDF。");
        }

        if (builder.isEmpty()) {
            builder.append("你好，我是 NetScope AI 网络 Agent。请发送网站 URL、监听需求或报告格式，我会围绕网络安全、网络测量和 Web 应用分析给出结果。");
        }
        return builder.toString();
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
                    .append(assignment.timed() ? "，限时 " + assignment.timeLimitMinutes() + " 分钟" : "")
                    .append("，状态：")
                    .append(assignment.status());
        }
        return builder.toString();
    }

    private String safeReason(ResponseStatusException ex) {
        String reason = ex.getReason();
        if (reason == null || reason.isBlank()) {
            return ex.getStatusCode().toString();
        }
        return reason;
    }

    private boolean containsAny(String text, String... keywords) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

