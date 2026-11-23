package com.sapphire.hawe.badge.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

public final class GroupMilestones {
	private GroupMilestones() {
	}

	public static Optional<LocalDateTime> hundredDaysAt(LocalDate base, LocalDate today) {
		return reached(base.plusDays(99), today);
	}

	public static Optional<LocalDateTime> oneYearAt(LocalDate base, LocalDate today) {
		return reached(base.plusYears(1), today);
	}

	private static Optional<LocalDateTime> reached(LocalDate day, LocalDate today) {
		return day.isAfter(today) ? Optional.empty() : Optional.of(day.atStartOfDay());
	}
}
