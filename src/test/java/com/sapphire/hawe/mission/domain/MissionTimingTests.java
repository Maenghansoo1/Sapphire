package com.sapphire.hawe.mission.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MissionTimingTests {
    @Test void deadlinesUseFirstStrictlyLaterWallTime() {
        LocalDate date = LocalDate.of(2026, 12, 31);
        assertEquals(date.atTime(23, 0), MissionTiming.deadlineAfter(date.atTime(9, 0), LocalTime.of(23, 0)));
        assertEquals(date.plusDays(1).atTime(23, 0),
                MissionTiming.deadlineAfter(date.atTime(23, 30), LocalTime.of(23, 0)));
        assertEquals(date.plusDays(1).atTime(23, 0),
                MissionTiming.deadlineAfter(date.atTime(23, 0), LocalTime.of(23, 0)));
        assertEquals(date.atTime(23, 0), MissionTiming.deadlineAfter(date.atTime(14, 0), LocalTime.of(23, 0)));
    }

    @Test void publishUsesFirstStrictlyLaterTimeAcrossYear() {
        LocalDate date = LocalDate.of(2026, 12, 31);
        assertEquals(date.atTime(9, 0), MissionTiming.scheduledPublishAt(date, LocalTime.of(9, 0)));
        assertEquals(date.atTime(9, 0),
                MissionTiming.nextPublishAfter(date.atTime(8, 59), LocalTime.of(9, 0)));
        assertEquals(date.plusDays(1).atTime(9, 0),
                MissionTiming.nextPublishAfter(date.atTime(9, 0), LocalTime.of(9, 0)));
        assertEquals(date.plusDays(1).atTime(9, 0),
                MissionTiming.nextPublishAfter(date.atTime(15, 0), LocalTime.of(9, 0)));
    }
}
