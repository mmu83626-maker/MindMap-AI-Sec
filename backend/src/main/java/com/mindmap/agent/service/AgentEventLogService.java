package com.mindmap.agent.service;

import com.mindmap.agent.dto.AgentCommandRequest;
import com.mindmap.agent.dto.AgentCommandResponse;
import com.mindmap.agent.dto.AgentRunRecord;
import com.mindmap.agent.dto.SkillExecutionRecord;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AgentEventLogService {

    private static final int MAX_RECORDS = 100;
    private final CopyOnWriteArrayList<AgentRunRecord> records = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<SkillExecutionRecord> skillExecutions = new CopyOnWriteArrayList<>();

    public AgentRunRecord append(AgentCommandRequest request, AgentCommandResponse response) {
        AgentRunRecord record = new AgentRunRecord(
                UUID.randomUUID().toString(),
                request.channel() == null ? "web" : request.channel(),
                request.userId(),
                request.chatId(),
                request.command(),
                response.answer(),
                response.plannedSkills(),
                OffsetDateTime.now()
        );
        records.add(record);
        while (records.size() > MAX_RECORDS) {
            records.remove(0);
        }
        return record;
    }

    public SkillExecutionRecord appendSkillExecution(
            String runId,
            String skillName,
            String skillTitle,
            String status,
            java.util.Map<String, Object> parameters,
            String summary,
            long durationMs
    ) {
        SkillExecutionRecord record = new SkillExecutionRecord(
                UUID.randomUUID().toString(),
                runId,
                skillName,
                skillTitle,
                status,
                parameters == null ? java.util.Map.of() : parameters,
                summary,
                durationMs,
                OffsetDateTime.now()
        );
        skillExecutions.add(record);
        while (skillExecutions.size() > MAX_RECORDS) {
            skillExecutions.remove(0);
        }
        return record;
    }

    public List<AgentRunRecord> recent(String channel, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_RECORDS));
        List<AgentRunRecord> snapshot = new ArrayList<>(records);
        return snapshot.stream()
                .filter(record -> channel == null || channel.isBlank() || channel.equals(record.channel()))
                .sorted(Comparator.comparing(AgentRunRecord::createdAt).reversed())
                .limit(safeLimit)
                .toList();
    }

    public List<SkillExecutionRecord> recentSkillExecutions(String skillName, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_RECORDS));
        List<SkillExecutionRecord> snapshot = new ArrayList<>(skillExecutions);
        return snapshot.stream()
                .filter(record -> skillName == null || skillName.isBlank() || skillName.equals(record.skillName()))
                .sorted(Comparator.comparing(SkillExecutionRecord::createdAt).reversed())
                .limit(safeLimit)
                .toList();
    }
}
