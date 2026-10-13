package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.mission.domain.GeneratedMission;
import com.sapphire.hawe.mission.domain.GenerationContext;
import com.sapphire.hawe.mission.domain.MissionTemplate;

public interface MissionGenerator {
    /** {@code context.recentMissions()}: 피해야 할 최근 미션, 최근 것부터 (같은 날 먼저 열린 슬롯 포함). */
    GeneratedMission generate(MissionTemplate template, long seed, GenerationContext context, boolean withTarget);
}
