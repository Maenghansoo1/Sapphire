package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.group.dto.GroupCreateRequest;
import com.sapphire.hawe.group.dto.MissionSlotRequest;
import com.sapphire.hawe.group.service.GroupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/** 실험을 켜고 모든 (그룹, 날짜)를 AI 군으로 두면, 발행된 미션에 군과 문장 출처가 저장된다 — 실제 DB (AI 응답은 가짜). */
@SpringBootTest(properties = {"hawe.mission.ai.enabled=true", "hawe.mission.ai.api-key=sk-test-only",
        "hawe.experiment.topic.enabled=true", "hawe.experiment.topic.ai-ratio=1.0"})
class ExperimentPublishTests {

    @MockitoBean AnthropicClient client;
    @Autowired GroupService groups;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void aiArmRecordsWhetherTheAiActuallyWroteTheMission() {
        when(client.callTool(anyString(), anyString(), any()))
                .thenAnswer(invocation -> AiMissionPublishTests.fakeAnswer(objectMapper, invocation.getArgument(1)));
        assertEquals(Map.of("generation_source", "AI", "experiment_arm", "AI"), publishFirstMission());

        // AI 가 실패해 고정 문장이 나가도 군은 AI (배정대로 센다)
        doThrow(new AiGenerationException("AI 응답 오류 HTTP 500")).when(client).callTool(anyString(), anyString(), any());
        assertEquals(Map.of("generation_source", "AI_FALLBACK", "experiment_arm", "AI"), publishFirstMission());
    }

    private Map<String, Object> publishFirstMission() {
        String email = "experiment-publish-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) "
                + "VALUES ('실험', ?, 'unused', NOW(), NOW())", email);
        Long owner = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Long groupId = null;
        try {
            groupId = groups.createGroup(owner, new GroupCreateRequest("실험 그룹", null, null, 1,
                    List.of(new MissionSlotRequest(1, "00:00", null)))).id();
            return jdbc.queryForMap("SELECT generation_source, experiment_arm FROM missions WHERE group_id = ?", groupId);
        } finally {
            if (groupId != null) groups.leaveGroup(owner, groupId, null);
            jdbc.update("DELETE FROM users WHERE id = ?", owner);
        }
    }
}
