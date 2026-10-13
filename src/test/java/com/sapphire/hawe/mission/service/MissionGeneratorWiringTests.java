package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.mission.domain.GenerationContext;
import com.sapphire.hawe.mission.domain.GenerationSource;
import com.sapphire.hawe.mission.domain.GroupSignals;
import com.sapphire.hawe.mission.domain.MissionTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** 기본 설정(AI 꺼짐)에서 발행기가 받는 생성기는 고정 문장으로 이어지는 FallbackMissionGenerator 다. */
@SpringBootTest
class MissionGeneratorWiringTests {

    @Autowired MissionGenerator generator;

    @Test
    void defaultGeneratorFallsBackToPlaceholders() {
        assertInstanceOf(FallbackMissionGenerator.class, generator);
        var mission = generator.generate(MissionTemplate.PHOTO_NOW, 1, new GenerationContext(LocalDate.of(2026, 10, 13),
                "FRIEND", List.of(), List.of(), GroupSignals.none(1, 1)), false);
        assertEquals(GenerationSource.PLACEHOLDER, mission.source());
    }
}
