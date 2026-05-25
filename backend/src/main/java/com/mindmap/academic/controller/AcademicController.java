package com.mindmap.academic.controller;

import com.mindmap.academic.dto.AcademicChatRequest;
import com.mindmap.academic.dto.AcademicChatResponse;
import com.mindmap.academic.dto.AssignmentListResponse;
import com.mindmap.academic.dto.FeishuMessageRequest;
import com.mindmap.academic.dto.FeishuMessageResponse;
import com.mindmap.academic.dto.AssignmentDto;
import com.mindmap.academic.dto.ManualAssignmentRequest;
import com.mindmap.academic.service.AcademicAgentService;
import com.mindmap.academic.service.AssignmentService;
import com.mindmap.academic.service.FeishuService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/academic")
@CrossOrigin(origins = "*")
public class AcademicController {

    private final AcademicAgentService academicAgentService;
    private final AssignmentService assignmentService;
    private final FeishuService feishuService;

    public AcademicController(
            AcademicAgentService academicAgentService,
            AssignmentService assignmentService,
            FeishuService feishuService
    ) {
        this.academicAgentService = academicAgentService;
        this.assignmentService = assignmentService;
        this.feishuService = feishuService;
    }

    @PostMapping("/chat")
    public AcademicChatResponse chat(@RequestBody AcademicChatRequest request) {
        return academicAgentService.chat(request);
    }

    @GetMapping("/assignments")
    public AssignmentListResponse assignments() {
        return assignmentService.assignmentSnapshot();
    }

    @PostMapping("/assignments")
    public AssignmentDto createAssignment(@RequestBody ManualAssignmentRequest request) {
        return assignmentService.addManualAssignment(request);
    }

    @PutMapping("/assignments/{id}")
    public AssignmentDto updateAssignment(@PathVariable String id, @RequestBody ManualAssignmentRequest request) {
        return assignmentService.updateManualAssignment(id, request);
    }

    @DeleteMapping("/assignments/{id}")
    public java.util.Map<String, Object> deleteAssignment(@PathVariable String id) {
        return java.util.Map.of("deleted", assignmentService.deleteManualAssignment(id));
    }

    @PostMapping("/sync/rain-classroom")
    public AssignmentListResponse syncRainClassroom() {
        return assignmentService.syncRainClassroom();
    }

    @PostMapping("/feishu/reminders")
    public FeishuMessageResponse sendReminder(@RequestBody FeishuMessageRequest request) {
        return feishuService.sendAssignmentReminder(request.assignmentId(), request.chatId(), request.userKey(), request.message());
    }
}
