package com.sapphire.hawe.badge.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class StreakCalculatorTests {
	@Test
	void boundaryDuplicatesGapsAndUnorderedDays() {
		LocalDate start = LocalDate.of(2026, 1, 1);
		var days = new ArrayList<DaySubmission>();
		for (int i = 0; i < 6; i++) days.add(new DaySubmission(start.plusDays(i),
				start.plusDays(i + 1).atStartOfDay().plusMinutes(2)));
		assertTrue(StreakCalculator.reachedAt(days, 7).isEmpty());
		days.add(new DaySubmission(start.plusDays(6), start.plusDays(7).atStartOfDay().plusMinutes(1)));
		days.add(new DaySubmission(start.plusDays(6), start.plusDays(7).atStartOfDay()));
		Collections.reverse(days);
		assertEquals(start.plusDays(7).atStartOfDay(), StreakCalculator.reachedAt(days, 7).orElseThrow());
		days.removeIf(d -> d.missionDate().equals(start.plusDays(3)));
		assertTrue(StreakCalculator.reachedAt(days, 7).isEmpty());
	}

	@Test
	void thirtyDaysAndEarliestCompletedRun() {
		LocalDate start = LocalDate.of(2026, 1, 1);
		List<DaySubmission> days = new ArrayList<>();
		for (int i = 0; i < 30; i++) days.add(new DaySubmission(start.plusDays(i),
				start.plusDays(i).atTime(9, 0)));
		for (int i = 40; i < 70; i++) days.add(new DaySubmission(start.plusDays(i),
				start.plusDays(i).atTime(8, 0)));
		assertEquals(start.plusDays(29).atTime(9, 0), StreakCalculator.reachedAt(days, 30).orElseThrow());
		assertThrows(IllegalArgumentException.class, () -> StreakCalculator.reachedAt(days, 0));
	}

	@Test
	void longestRunIgnoresDuplicateAndUnorderedDates() {
		var base = LocalDate.of(2026, 1, 1);
		assertEquals(0, StreakCalculator.longestRun(List.of()));
		assertEquals(2, StreakCalculator.longestRun(List.of(
				new DaySubmission(base.plusDays(3), base.plusDays(3).atStartOfDay()),
				new DaySubmission(base, base.atStartOfDay()),
				new DaySubmission(base.plusDays(1), base.plusDays(1).atStartOfDay()),
				new DaySubmission(base, base.atTime(10, 0)))));
	}
}
