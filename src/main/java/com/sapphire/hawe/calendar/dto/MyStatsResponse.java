package com.sapphire.hawe.calendar.dto;

import java.util.Map;

public record MyStatsResponse(long completedMissions, int streakDays, long groupCount,
        Map<String, Long> byMedia) {
}
