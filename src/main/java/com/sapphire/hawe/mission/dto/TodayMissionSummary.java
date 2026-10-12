package com.sapphire.hawe.mission.dto;

public record TodayMissionSummary(Long missionId, String media, String content,
        long submittedCount, boolean submittedByMe) {
}
