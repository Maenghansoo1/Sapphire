package com.sapphire.hawe.badge.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ScheduledActivity(List<DaySubmission> onTimeDays, List<LocalDateTime> perfectFitTimes,
		int crowdMaxInteractions, List<GroupBase> groups, List<PerfectMonthRow> perfectMonthRows,
		LocalDate perfectMonthEnd, int perfectMonthMinMissions) {
}
