package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.group.dto.GroupCreateRequest;
import com.sapphire.hawe.group.dto.MissionSlotRequest;
import com.sapphire.hawe.group.service.GroupService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AI 를 켠 설정(AI 응답은 가짜)으로 그룹을 만들면 첫 미션이 검증된 AI 문장으로 열린다 — 실제 DB. */
@SpringBootTest(properties = {"hawe.mission.ai.enabled=true", "hawe.mission.ai.api-key=sk-test-only"})
class AiMissionPublishTests {

    @MockitoBean AnthropicClient client;
    @Autowired GroupService groups;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void firstMissionUsesTheCheckedAiTextAndTheServerChosenTarget() {
        AtomicReference<Map<String, Object>> answered = new AtomicReference<>();
        when(client.callTool(anyString(), anyString(), any())).thenAnswer(invocation -> {
            Map<String, Object> answer = fakeAnswer(invocation.getArgument(1));
            answered.set(answer);
            return answer;
        });
        String email = "ai-publish-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) "
                + "VALUES ('비밀닉네임', ?, 'unused', NOW(), NOW())", email);
        Long owner = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Long groupId = null;
        try {
            groupId = groups.createGroup(owner, new GroupCreateRequest("비밀그룹이름", "COUPLE", null, 1,
                    List.of(new MissionSlotRequest(1, "00:00", null)))).id();

            Map<String, Object> mission = jdbc.queryForMap(
                    "SELECT content, category, template, params FROM missions WHERE group_id = ?", groupId);
            assertEquals(answered.get().get("content"), mission.get("content"));
            assertEquals("감성", mission.get("category"));
            JsonNode params = objectMapper.readTree((String) mission.get("params"));
            boolean targeted = ((String) answered.get().get("content")).contains("[대상]");
            assertEquals(targeted, params.has("targetUserId"));
            if (targeted) assertEquals(owner.longValue(), params.path("targetUserId").asLong());  // 대상은 서버가 정한다

            ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
            verify(client, atLeastOnce()).callTool(anyString(), input.capture(), any());
            for (String sent : input.getAllValues()) {
                assertFalse(sent.contains("비밀닉네임"));
                assertFalse(sent.contains("비밀그룹이름"));
                assertFalse(sent.contains(email));
                assertTrue(sent.contains("\"그룹 관계\":\"연인\""));
            }
        } finally {
            if (groupId != null) groups.leaveGroup(owner, groupId, null);
            jdbc.update("DELETE FROM users WHERE id = ?", owner);
        }
    }

    /** 받은 입력의 틀·대상에 맞는 정상 답 (기본 발행 풀 7종). */
    private Map<String, Object> fakeAnswer(String userMessage) {
        JsonNode input = objectMapper.readTree(userMessage.substring(userMessage.indexOf('\n') + 1));
        boolean target = "대상 있음".equals(input.path("대상").asString());
        String code = input.path("틀").path("코드").asString();
        Map<String, Object> params = new LinkedHashMap<>();
        String content = switch (code) {
            case "PHOTO_NOW" -> { params.put("subject", "지금 제일 열일 중인 물건"); yield "지금 이 순간 제일 열일 중인 물건은?"; }
            case "PHOTO_GALLERY" -> { params.put("condition", "올해 찍은 하늘 중 원픽"); yield "올해 찍은 하늘 사진 중 원픽 하나"; }
            case "PHOTO_COLOR" -> { params.put("color", "초록색"); yield "주변에서 제일 의외인 초록색 찾기"; }
            case "TEXT_ACROSTIC" -> { params.put("word", "보고픔"); yield "'보고픔' 삼행시 가자"; }
            case "TEXT_FILL_BLANK" -> { params.put("sentence", "오늘의 [대상]에게 어울리는 노래 제목은 ___"); yield "오늘의 [대상]에게 어울리는 노래는?"; }
            case "DRAW_SKETCH" -> {
                params.put("subject", target ? "[대상]" : "월요일 아침의 나");
                params.put("seconds", "20");
                yield target ? "20초 컷으로 [대상] 그리기" : "20초 컷으로 월요일 아침의 나";
            }
            case "DRAW_A_TO_B" -> {
                params.put("a", target ? "[대상]" : "월요일");
                params.put("b", target ? "판타지 게임 캐릭터" : "동물");
                yield target ? "판타지 게임 캐릭터 버전의 [대상]" : "월요일을 동물로 그리면?";
            }
            default -> throw new IllegalStateException("기본 풀에 없는 틀: " + code);
        };
        return Map.of("content", content, "category", "감성", "params", params);
    }
}
