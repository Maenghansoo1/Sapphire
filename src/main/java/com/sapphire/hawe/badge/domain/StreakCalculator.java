package com.sapphire.hawe.badge.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class StreakCalculator {
	private StreakCalculator() {
	}

	public static Optional<LocalDateTime> reachedAt(java.util.List<DaySubmission> days, int n) {
		if (n < 1) throw new IllegalArgumentException("Streak length must be positive");
		Map<LocalDate, LocalDateTime> firstByDate = new TreeMap<>();
		for (DaySubmission day : days)
			firstByDate.merge(day.missionDate(), day.firstOnTimeSubmittedAt(),
				(a, b) -> a.isBefore(b) ? a : b);
		LocalDate previous = null;
		int length = 0;
		for (var entry : firstByDate.entrySet()) {
			length = previous != null && previous.plusDays(1).equals(entry.getKey()) ? length + 1 : 1;
			if (length == n) return Optional.of(entry.getValue());
			previous = entry.getKey();
		}
		return Optional.empty();
	}

	public static int longestRun(java.util.List<DaySubmission> days) {
		var dates = new java.util.TreeSet<LocalDate>();
		for (DaySubmission day : days) dates.add(day.missionDate());
		LocalDate previous = null;
		int run = 0, longest = 0;
		for (LocalDate date : dates) {
			run = previous != null && previous.plusDays(1).equals(date) ? run + 1 : 1;
			longest = Math.max(longest, run);
			previous = date;
		}
		return longest;
	}
}
