package com.mindmap.academic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindmap.academic.dto.AssignmentDto;
import com.mindmap.academic.dto.AssignmentListResponse;
import com.mindmap.academic.dto.ManualAssignmentRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AssignmentService {

    private final ObjectMapper objectMapper;
    private final Path rainClassroomCachePath;
    private final Path manualAssignmentsPath;
    private final CopyOnWriteArrayList<AssignmentDto> manualAssignments;

    private final List<AssignmentDto> demoAssignments = List.of(
            new AssignmentDto(
                    "rain-demo-001",
                    "演示数据",
                    "机器学习",
                    "第三周梯度下降实验报告",
                    "待完成",
                    OffsetDateTime.now().plusDays(2).withHour(23).withMinute(59).withSecond(0).withNano(0),
                    "https://www.yuketang.cn/",
                    false,
                    null,
                    "这是演示作业。录入真实作业后会优先显示你的手动数据。"
            ),
            new AssignmentDto(
                    "manual-demo-001",
                    "演示数据",
                    "信息安全导论",
                    "访问控制模型思维导图",
                    "待拆解",
                    OffsetDateTime.now().plusDays(5).withHour(20).withMinute(0).withSecond(0).withNano(0),
                    "",
                    true,
                    45,
                    "限时任务示例。"
            )
    );

    public AssignmentService(
            ObjectMapper objectMapper,
            @Value("${app.classroom.rain-cache-path:${user.dir}/data/rain-classroom-assignments.json}") String rainClassroomCachePath,
            @Value("${app.assignments.manual-path:${user.dir}/data/manual-assignments.json}") String manualAssignmentsPath
    ) {
        this.objectMapper = objectMapper;
        this.rainClassroomCachePath = Path.of(rainClassroomCachePath);
        this.manualAssignmentsPath = Path.of(manualAssignmentsPath);
        this.manualAssignments = new CopyOnWriteArrayList<>(loadManualAssignments());
    }

    public AssignmentListResponse assignmentSnapshot() {
        List<AssignmentDto> manual = sorted(manualAssignments);
        if (!manual.isEmpty()) {
            return new AssignmentListResponse(
                    "manual",
                    "manual",
                    "已读取手动录入的真实作业数据。",
                    OffsetDateTime.now(),
                    manual
            );
        }

        return demoSnapshot("暂无手动录入作业，当前显示演示数据。");
    }

    public AssignmentListResponse syncRainClassroom() {
        return assignmentSnapshot();
    }

    public AssignmentDto addManualAssignment(ManualAssignmentRequest request) {
        AssignmentDto assignment = toAssignment(request, UUID.randomUUID().toString());
        manualAssignments.add(assignment);
        saveManualAssignments();
        return assignment;
    }

    public AssignmentDto updateManualAssignment(String id, ManualAssignmentRequest request) {
        AssignmentDto assignment = toAssignment(request, id);
        manualAssignments.removeIf(item -> item.id().equals(id));
        manualAssignments.add(assignment);
        saveManualAssignments();
        return assignment;
    }

    public boolean deleteManualAssignment(String id) {
        boolean removed = manualAssignments.removeIf(item -> item.id().equals(id));
        if (removed) {
            saveManualAssignments();
        }
        return removed;
    }

    public List<AssignmentDto> listAssignments() {
        return sorted(assignmentSnapshot().assignments());
    }

    public List<AssignmentDto> upcomingAssignments(int limit) {
        return listAssignments().stream().limit(limit).toList();
    }

    public AssignmentDto findById(String assignmentId) {
        return listAssignments().stream()
                .filter(assignment -> assignment.id().equals(assignmentId))
                .findFirst()
                .orElse(null);
    }

    public String digest(int limit) {
        List<AssignmentDto> upcoming = upcomingAssignments(limit);
        if (upcoming.isEmpty()) {
            return "当前没有作业。";
        }
        StringBuilder builder = new StringBuilder("最近作业：");
        for (AssignmentDto assignment : upcoming) {
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

    private AssignmentDto toAssignment(ManualAssignmentRequest request, String id) {
        String course = required(request.course(), "课程名称不能为空。");
        String title = required(request.title(), "作业名称不能为空。");
        OffsetDateTime deadline = parseDeadline(required(request.deadline(), "截止时间不能为空。"));
        Integer minutes = request.timed() ? Math.max(1, request.timeLimitMinutes() == null ? 60 : request.timeLimitMinutes()) : null;
        return new AssignmentDto(
                id,
                "手动录入",
                course,
                title,
                blank(request.status()) ? "待完成" : request.status().trim(),
                deadline,
                "",
                request.timed(),
                minutes,
                blank(request.note()) ? "" : request.note().trim()
        );
    }

    private AssignmentListResponse loadRainClassroomCache() {
        if (!Files.exists(rainClassroomCachePath)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(rainClassroomCachePath.toFile());
            List<AssignmentDto> assignments = objectMapper.readerForListOf(AssignmentDto.class)
                    .readValue(root.path("assignments"));
            if (assignments.isEmpty()) {
                return null;
            }
            return new AssignmentListResponse(
                    root.path("source").asText("rain-classroom"),
                    root.path("status").asText("real-cache"),
                    root.path("message").asText("已读取本地雨课堂真实缓存。"),
                    parseTime(root.path("syncedAt").asText("")),
                    sorted(assignments)
            );
        } catch (IOException ex) {
            return demoSnapshot("雨课堂缓存读取失败，已回退到演示数据：" + ex.getMessage());
        }
    }

    private List<AssignmentDto> loadManualAssignments() {
        if (!Files.exists(manualAssignmentsPath)) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(AssignmentDto.class).readValue(manualAssignmentsPath.toFile());
        } catch (IOException ex) {
            return List.of();
        }
    }

    private void saveManualAssignments() {
        try {
            Files.createDirectories(manualAssignmentsPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(manualAssignmentsPath.toFile(), sorted(manualAssignments));
        } catch (IOException ex) {
            throw new IllegalArgumentException("保存手动作业失败：" + ex.getMessage(), ex);
        }
    }

    private AssignmentListResponse demoSnapshot(String message) {
        return new AssignmentListResponse("demo", "demo", message, OffsetDateTime.now(), sorted(demoAssignments));
    }

    private List<AssignmentDto> sorted(List<AssignmentDto> assignments) {
        return new ArrayList<>(assignments).stream()
                .sorted(Comparator.comparing(AssignmentDto::deadline))
                .toList();
    }

    private OffsetDateTime parseDeadline(String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ignored) {
            try {
                return java.time.LocalDateTime.parse(value.replace(" ", "T"))
                        .atOffset(java.time.ZoneOffset.ofHours(8));
            } catch (Exception ex) {
                throw new IllegalArgumentException("截止时间格式不正确，请使用 2026-05-12T23:59 或 2026-05-12 23:59。");
            }
        }
    }

    private OffsetDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return OffsetDateTime.now();
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ex) {
            return OffsetDateTime.now();
        }
    }

    private String required(String value, String message) {
        if (blank(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
