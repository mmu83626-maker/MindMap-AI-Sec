package com.mindmap.agent.controller;

import com.mindmap.agent.dto.AgentCommandRequest;
import com.mindmap.agent.dto.AgentCommandResponse;
import com.mindmap.agent.dto.AgentRunRecord;
import com.mindmap.agent.dto.SkillDownloadRequest;
import com.mindmap.agent.dto.SkillDefinition;
import com.mindmap.agent.dto.SkillImportRequest;
import com.mindmap.agent.dto.SkillExecutionRecord;
import com.mindmap.agent.service.AgentArtifactService;
import com.mindmap.agent.service.AgentEventLogService;
import com.mindmap.agent.service.AgentOrchestratorService;
import com.mindmap.agent.service.SkillRegistryService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = "*")
public class AgentController {

    private final SkillRegistryService skillRegistryService;
    private final AgentOrchestratorService agentOrchestratorService;
    private final AgentEventLogService agentEventLogService;
    private final AgentArtifactService agentArtifactService;

    public AgentController(
            SkillRegistryService skillRegistryService,
            AgentOrchestratorService agentOrchestratorService,
            AgentEventLogService agentEventLogService,
            AgentArtifactService agentArtifactService
    ) {
        this.skillRegistryService = skillRegistryService;
        this.agentOrchestratorService = agentOrchestratorService;
        this.agentEventLogService = agentEventLogService;
        this.agentArtifactService = agentArtifactService;
    }

    @GetMapping("/skills")
    public List<SkillDefinition> skills() {
        return skillRegistryService.listSkills();
    }

    @GetMapping("/skills/marketplace")
    public List<SkillDefinition> marketplace() {
        return skillRegistryService.marketplace();
    }

    @PostMapping("/skills/import")
    public SkillDefinition importSkill(@RequestBody SkillImportRequest request) {
        if (request.skill() != null) {
            return skillRegistryService.importSkill(request.skill(), request.signature());
        }
        return skillRegistryService.importFromJson(request.json(), request.signature());
    }

    @PostMapping("/skills/download")
    public SkillDefinition downloadSkill(@RequestBody SkillDownloadRequest request) {
        return skillRegistryService.downloadSkill(request.sourceUrl());
    }

    @GetMapping("/skills/{skillName}/invoke")
    public Map<String, Object> invocationHints(@PathVariable String skillName) {
        return skillRegistryService.invocationHints(skillName);
    }

    @PostMapping("/run")
    public AgentCommandResponse run(@RequestBody AgentCommandRequest request) {
        return agentOrchestratorService.run(request);
    }

    @GetMapping("/artifacts/{id}")
    public ResponseEntity<Resource> artifact(@PathVariable String id) {
        String filename = agentArtifactService.filename(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(agentArtifactService.load(id));
    }

    @GetMapping("/events")
    public List<AgentRunRecord> events(
            @RequestParam(required = false) String channel,
            @RequestParam(defaultValue = "30") int limit
    ) {
        return agentEventLogService.recent(channel, limit);
    }

    @GetMapping("/skill-executions")
    public List<SkillExecutionRecord> skillExecutions(
            @RequestParam(required = false) String skillName,
            @RequestParam(defaultValue = "30") int limit
    ) {
        return agentEventLogService.recentSkillExecutions(skillName, limit);
    }
}
