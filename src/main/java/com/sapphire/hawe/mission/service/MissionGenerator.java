package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.mission.domain.GeneratedMission;
import com.sapphire.hawe.mission.domain.MissionTemplate;

public interface MissionGenerator {
    GeneratedMission generate(MissionTemplate template, long seed, String previousContent, boolean withTarget);
}
