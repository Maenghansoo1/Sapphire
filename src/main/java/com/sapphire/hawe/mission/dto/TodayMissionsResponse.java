package com.sapphire.hawe.mission.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TodayMissionsResponse(LocalDate date, int dailyMissionCount,
        LocalDateTime nextPublishAt, List<MissionResponse> missions) {
}
