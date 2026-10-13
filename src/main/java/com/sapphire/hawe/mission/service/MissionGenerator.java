package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.mission.domain.GeneratedMission;
import com.sapphire.hawe.mission.domain.MissionTemplate;

import java.util.List;

public interface MissionGenerator {
    /** {@code recentContents}: 피해야 할 최근 문장, 최근 것부터 (같은 날 먼저 열린 슬롯 포함). */
    GeneratedMission generate(MissionTemplate template, long seed, List<String> recentContents, boolean withTarget);
}
