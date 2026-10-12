package com.sapphire.hawe.mission.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public final class MissionTiming {

    private MissionTiming() {
    }

    public static LocalDateTime scheduledPublishAt(LocalDate date, LocalTime publishTime) {
        return date.atTime(publishTime);
    }

    public static LocalDateTime deadlineAfter(LocalDateTime publishedAt, LocalTime deadlineTime) {
        LocalDateTime candidate = publishedAt.toLocalDate().atTime(deadlineTime);
        return candidate.isAfter(publishedAt) ? candidate : candidate.plusDays(1);
    }

    public static LocalDateTime nextPublishAfter(LocalDateTime after, LocalTime publishTime) {
        LocalDateTime candidate = after.toLocalDate().atTime(publishTime);
        return candidate.isAfter(after) ? candidate : candidate.plusDays(1);
    }
}
