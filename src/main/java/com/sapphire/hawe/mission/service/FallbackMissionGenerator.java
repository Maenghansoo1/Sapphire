package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.mission.domain.GeneratedMission;
import com.sapphire.hawe.mission.domain.GenerationContext;
import com.sapphire.hawe.mission.domain.GenerationSource;
import com.sapphire.hawe.mission.domain.MissionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 발행기가 쓰는 생성기. AI 를 쓸 수 있으면 먼저 쓰고, 꺼져 있거나 실패하면 고정 문장을 쓴다 — 어떤 경우에도 예외를
 * 던지지 않아 AI 장애가 발행을 막지 않는다. 실패 사유만 로그에 남긴다(키·응답 본문은 남기지 않는다).
 */
@Primary
@Component
public class FallbackMissionGenerator implements MissionGenerator {

    private static final Logger log = LoggerFactory.getLogger(FallbackMissionGenerator.class);

    private final AiMissionGenerator ai;
    private final PlaceholderMissionGenerator placeholder;

    public FallbackMissionGenerator(AiMissionGenerator ai, PlaceholderMissionGenerator placeholder) {
        this.ai = ai;
        this.placeholder = placeholder;
    }

    @Override
    public GeneratedMission generate(MissionTemplate template, long seed, GenerationContext context, boolean withTarget) {
        if (!ai.available()) return placeholder.generate(template, seed, context, withTarget);
        try {
            return ai.generate(template, context, withTarget);
        } catch (RuntimeException e) {
            String reason = e instanceof AiGenerationException ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("AI mission generation failed for {}, using a placeholder: {}", template, reason);
            return placeholder.generate(template, seed, context, withTarget).withSource(GenerationSource.AI_FALLBACK);
        }
    }
}
