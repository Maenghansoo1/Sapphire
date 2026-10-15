package com.sapphire.hawe.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.MonthDay;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class MissionAiPropertiesTests {

    @Autowired MissionAiProperties properties;

    @Test
    void applicationPropertiesKeepAiOffInTests() {
        // pom 의 surefire 설정이 .env 와 상관없이 꺼 둔다 — 테스트가 실제 AI 를 부르지 않게
        assertFalse(properties.enabled());
        assertFalse(properties.usable());
        // 모델은 .env 의 LLM_MODEL(예: bedrock-haiku)을 따르므로 이름은 고정하지 않는다
        assertFalse(properties.model().isBlank());
        assertEquals(List.of("일상", "감성", "추억", "유머"), properties.categories());
        assertEquals(List.of("classpath:prompts/mission-create-prompt.md", "classpath:prompts/mission-template-prompt.md"),
                properties.promptPaths());
    }

    @Test
    void missingValuesFallBackToDefaults() {
        MissionAiProperties defaults = new MissionAiProperties(false, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
        assertEquals("https://api.anthropic.com", defaults.baseUrl());
        assertEquals("", defaults.apiKey());
        assertEquals(400, defaults.maxTokens());
        assertEquals(0.9, defaults.temperature());
        assertEquals(2, defaults.maxAttempts());
        assertEquals(Duration.ofSeconds(20), defaults.readTimeout());
        assertEquals(Duration.ofSeconds(40), defaults.totalTimeout());
        assertEquals(MissionAiProperties.DEFAULT_CATEGORIES, defaults.categories());
        assertTrue(defaults.promptPaths().isEmpty() && defaults.blockedWords().isEmpty() && defaults.calendarDays().isEmpty());
    }

    @Test
    void aiNeedsBothTheSwitchAndAKey() {
        assertFalse(create(true, " ", List.of()).usable());
        assertFalse(create(false, "sk-test", List.of()).usable());
        assertTrue(create(true, "sk-test", List.of()).usable());
    }

    @Test
    void keyNeverShowsInToString() {
        String text = create(true, "sk-ant-secret-value", List.of()).toString();
        assertFalse(text.contains("sk-ant-secret-value"));
        assertTrue(text.contains("(설정됨)"));
    }

    @Test
    void specialDaysAreParsedOrRejectedAtStartup() {
        assertEquals(Map.of(MonthDay.of(12, 25), "크리스마스", MonthDay.of(2, 14), "밸런타인데이"),
                create(false, "", List.of("12-25=크리스마스", " 02-14 = 밸런타인데이 ")).calendarDays());
        for (String wrong : List.of("12/25=크리스마스", "13-01=없는 달", "12-25", "12-25=", "크리스마스"))
            assertThrows(IllegalArgumentException.class, () -> create(false, "", List.of(wrong)));
    }

    private static MissionAiProperties create(boolean enabled, String key, List<String> specialDays) {
        return new MissionAiProperties(enabled, null, key, null, null, null, null, null, null, null, null, null, null,
                specialDays);
    }
}
