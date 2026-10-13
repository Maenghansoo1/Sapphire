package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.config.MissionAiProperties;
import com.sapphire.hawe.mission.domain.GeneratedMission;
import com.sapphire.hawe.mission.domain.GenerationContext;
import com.sapphire.hawe.mission.domain.GenerationSource;
import com.sapphire.hawe.mission.domain.GroupSignals;
import com.sapphire.hawe.mission.domain.MissionTemplate;
import com.sapphire.hawe.mission.domain.RecentMission;
import com.sapphire.hawe.mission.domain.SpecialDay;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AiMissionGeneratorTests {

    private static final GenerationContext CONTEXT = new GenerationContext(LocalDate.of(2026, 10, 13), "COUPLE",
            List.of(new RecentMission("오늘 하늘 사진 찍어 줘", "감성", "PHOTO_NOW"),
                    new RecentMission("목소리로 인사해 줘", null, "VOICE_LINE")),
            List.of(new SpecialDay(SpecialDay.Kind.COUPLE_MILESTONE, "우리의 100일", 0)),
            new GroupSignals(2, 83, List.of(new GroupSignals.MissionStat("TEXT_BALANCE", "짜장 vs 짬뽕", 1.0, 1.5)),
                    Map.of("TEXT", 0.75)));

    private final AnthropicClient client = mock(AnthropicClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong now = new AtomicLong();

    @Test
    void joinsTeamPromptsInOrderAndAlwaysAddsTheServerGuide(@TempDir Path dir) throws Exception {
        Path create = Files.writeString(dir.resolve("create.md"), "생성 프롬프트 본문", StandardCharsets.UTF_8);
        Path detail = Files.writeString(dir.resolve("detail.md"), "틀별 상세 본문", StandardCharsets.UTF_8);
        String prompt = generator(List.of(create.toUri().toString(), detail.toUri().toString())).systemPrompt();
        assertTrue(prompt.startsWith("생성 프롬프트 본문"));
        assertTrue(prompt.indexOf("틀별 상세 본문") > prompt.indexOf("생성 프롬프트 본문"));
        assertTrue(prompt.endsWith(guide().strip()));
        assertFalse(prompt.contains("임시 기본 프롬프트"));

        // 하나만 있으면 그것만, 하나도 없으면 임시 기본 프롬프트
        String one = generator(List.of(dir.resolve("없음.md").toUri().toString(), detail.toUri().toString())).systemPrompt();
        assertTrue(one.startsWith("틀별 상세 본문"));
        String fallback = generator(List.of(dir.resolve("없음.md").toUri().toString())).systemPrompt();
        assertTrue(fallback.startsWith("임시 기본 프롬프트"));
        assertTrue(fallback.endsWith(guide().strip()));
        assertTrue(generator(List.of()).systemPrompt().startsWith("임시 기본 프롬프트"));
    }

    @Test
    void committedPromptsHaveNoBracesForTeamPromptCompatibility() {
        String prompt = generator(List.of()).systemPrompt();
        assertFalse(prompt.contains("{") || prompt.contains("}"));
    }

    @Test
    void inputDescribesTheMissionWithoutServerOnlyValues() {
        JsonNode input = json(generator(List.of()).input(MissionTemplate.TEXT_FILL_BLANK, CONTEXT, true));
        assertEquals("2026-10-13 화요일", input.path("오늘").asString());
        assertEquals("연인", input.path("그룹 관계").asString());
        assertEquals("TEXT_FILL_BLANK", input.path("틀").path("코드").asString());
        assertEquals("빈칸 채우기", input.path("틀").path("이름").asString());
        assertTrue(input.path("틀").path("빈칸").has("sentence"));
        assertFalse(input.path("틀").path("빈칸").has("targetUserId"));  // 대상은 서버가 정한다
        assertEquals("대상 있음", input.path("대상").asString());
        assertEquals("오늘 하늘 사진 찍어 줘", input.path("최근 미션").get(0).path("문장").asString());
        assertEquals("지금 이 순간", input.path("최근 미션").get(0).path("틀").asString());
        assertEquals("VOICE_LINE", input.path("최근 미션").get(1).path("틀").asString());   // 없어진 틀은 코드 그대로
        assertFalse(input.path("최근 미션").get(1).has("카테고리"));
        assertEquals(4, input.path("카테고리 목록").size());
        assertEquals("우리의 100일", input.path("특별한 날").get(0).path("이름").asString());
        assertEquals(0, input.path("특별한 날").get(0).path("남은 날").asInt());
        assertEquals(83, input.path("그룹 기록").path("함께한 날").asInt());
        assertEquals("밸런스 게임", input.path("그룹 기록").path("최근 미션 참여").get(0).path("틀").asString());
        assertEquals(0.75, input.path("그룹 기록").path("매체별 참여율").path("글").asDouble());
        assertEquals("대상 없음", json(generator(List.of()).input(MissionTemplate.PHOTO_NOW, CONTEXT, false))
                .path("대상").asString());
    }

    @Test
    void retriesAnInvalidAnswerAndReturnsTheCheckedMission() {
        when(client.callTool(anyString(), anyString(), eq(AiMissionGenerator.TOOL)))
                .thenReturn(answer("지금 컵 찍어 와", "감동", Map.of("subject", "컵")))     // 카테고리 틀림
                .thenReturn(answer("지금 컵 찍어 와", "일상", Map.of("subject", "컵")));
        AiMissionGenerator generator = generator(List.of());

        GeneratedMission mission = generator.generate(MissionTemplate.PHOTO_NOW, CONTEXT, false);

        assertEquals(new GeneratedMission("지금 컵 찍어 와", "일상", Map.of("subject", "컵"), GenerationSource.AI), mission);
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(client, times(2)).callTool(system.capture(), anyString(), any());
        assertEquals(generator.systemPrompt(), system.getValue());
    }

    @Test
    void givesUpAfterMaxAttemptsWithTheLastReason() {
        when(client.callTool(anyString(), anyString(), any()))
                .thenThrow(new AiGenerationException("AI 응답 오류 HTTP 529"))
                .thenReturn(answer("오늘 하늘 사진 찍어 줘", "일상", Map.of("subject", "하늘")));   // 최근 미션과 같음
        AiGenerationException error = assertThrows(AiGenerationException.class,
                () -> generator(List.of()).generate(MissionTemplate.PHOTO_NOW, CONTEXT, false));
        assertEquals("검증 실패(최근 미션과 같거나 거의 같은 문장)", error.getMessage());
        verify(client, times(2)).callTool(anyString(), anyString(), any());
    }

    @Test
    void stopsRetryingWhenTheTotalTimeWouldBeExceeded() {
        // 첫 시도가 25초 걸림 → 25 + 읽기 제한 20 > 총 제한 40 → 두 번째 시도는 하지 않는다
        when(client.callTool(anyString(), anyString(), any())).thenAnswer(invocation -> {
            now.addAndGet(Duration.ofSeconds(25).toNanos());
            throw new AiGenerationException("AI 연결 실패 또는 시간 초과");
        });
        assertThrows(AiGenerationException.class,
                () -> generator(List.of()).generate(MissionTemplate.PHOTO_NOW, CONTEXT, false));
        verify(client, times(1)).callTool(anyString(), anyString(), any());
    }

    @Test
    void availableOnlyWhenEnabledWithAKey() {
        assertTrue(generator(List.of()).available());
        AiMissionGenerator off = new AiMissionGenerator(properties(false, "sk-test", List.of()), client, objectMapper,
                new DefaultResourceLoader());
        assertFalse(off.available());
    }

    private AiMissionGenerator generator(List<String> promptPaths) {
        return new AiMissionGenerator(properties(true, "sk-test", promptPaths), client, objectMapper,
                new DefaultResourceLoader(), now::get);
    }

    private static MissionAiProperties properties(boolean enabled, String key, List<String> promptPaths) {
        return new MissionAiProperties(enabled, null, key, null, null, null, null, null, null, null, promptPaths, null,
                null, null);
    }

    private static Map<String, Object> answer(String content, String category, Map<String, Object> params) {
        return Map.of("content", content, "category", category, "params", params);
    }

    private JsonNode json(String input) {
        assertTrue(input.startsWith("지금 만들 미션의 정보야."));
        return objectMapper.readTree(input.substring(input.indexOf('\n') + 1));
    }

    private static String guide() {
        try (var stream = AiMissionGeneratorTests.class.getResourceAsStream("/mission-ai/server-guide.txt")) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
