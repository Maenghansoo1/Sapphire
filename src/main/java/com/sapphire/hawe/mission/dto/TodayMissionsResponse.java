package com.sapphire.hawe.mission.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record TodayMissionsResponse(LocalDate date, int dailyMissionCount,
        OffsetDateTime nextPublishAt, List<MissionResponse> missions) {
}
