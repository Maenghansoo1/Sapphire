package com.sapphire.hawe.calendar.dto;

import com.sapphire.hawe.calendar.domain.Stamp;

public record CalendarMission(Long missionId, String media, String content,
        long submittedCount, long memberCount, Stamp stamp) {
}
