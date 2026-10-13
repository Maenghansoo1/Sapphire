package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.mission.domain.GeneratedMission;
import com.sapphire.hawe.mission.domain.GenerationContext;
import com.sapphire.hawe.mission.domain.GenerationSource;
import com.sapphire.hawe.mission.domain.GroupSignals;
import com.sapphire.hawe.mission.domain.MissionTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class FallbackMissionGeneratorTests {

    private static final GenerationContext CONTEXT = new GenerationContext(LocalDate.of(2026, 10, 13), "FRIEND",
            List.of(), List.of(), GroupSignals.none(3, 10));

    private final AiMissionGenerator ai = mock(AiMissionGenerator.class);
    private final PlaceholderMissionGenerator placeholder = new PlaceholderMissionGenerator();
    private final FallbackMissionGenerator generator = new FallbackMissionGenerator(ai, placeholder);

    @Test
    void usesPlaceholderWhenAiIsOff() {
        GeneratedMission mission = generator.generate(MissionTemplate.PHOTO_COLOR, 7, CONTEXT, false);
        assertEquals(placeholder.generate(MissionTemplate.PHOTO_COLOR, 7, CONTEXT, false), mission);
        assertEquals(GenerationSource.PLACEHOLDER, mission.source());
        verify(ai, never()).generate(any(), any(), anyBoolean());
    }

    @Test
    void usesTheAiMissionWhenItWorks() {
        GeneratedMission fromAi = new GeneratedMission("오늘 눈에 띈 노란색 원픽", "일상", Map.of("color", "노란색"),
                GenerationSource.AI);
        when(ai.available()).thenReturn(true);
        when(ai.generate(MissionTemplate.PHOTO_COLOR, CONTEXT, false)).thenReturn(fromAi);
        assertSame(fromAi, generator.generate(MissionTemplate.PHOTO_COLOR, 7, CONTEXT, false));
    }

    @Test
    void anyAiFailureFallsBackWithoutThrowing(CapturedOutput output) {
        when(ai.available()).thenReturn(true);
        when(ai.generate(any(), any(), anyBoolean()))
                .thenThrow(new AiGenerationException("AI 응답 오류 HTTP 529"))
                .thenThrow(new IllegalStateException("unexpected sk-ant-secret"));

        GeneratedMission first = generator.generate(MissionTemplate.TEXT_FILL_BLANK, 7, CONTEXT, true);
        GeneratedMission second = generator.generate(MissionTemplate.PHOTO_COLOR, 7, CONTEXT, false);

        assertEquals(GenerationSource.AI_FALLBACK, first.source());
        assertEquals(placeholder.generate(MissionTemplate.TEXT_FILL_BLANK, 7, CONTEXT, true).content(), first.content());
        assertEquals(GenerationSource.AI_FALLBACK, second.source());
        assertTrue(output.getOut().contains("AI mission generation failed for TEXT_FILL_BLANK, using a placeholder: AI 응답 오류 HTTP 529"));
        // 예상 못 한 예외는 종류만 남긴다 (메시지에 무엇이 들었을지 모르므로)
        assertTrue(output.getOut().contains("using a placeholder: IllegalStateException"));
        assertFalse(output.getOut().contains("sk-ant-secret"));
    }
}
