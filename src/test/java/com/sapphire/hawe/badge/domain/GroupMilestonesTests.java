package com.sapphire.hawe.badge.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GroupMilestonesTests {
	@Test
	void hundredthDayIncludesBaseDay() {
		var base = LocalDate.of(2026, 1, 1);
		assertTrue(GroupMilestones.hundredDaysAt(base, base.plusDays(98)).isEmpty());
		assertEquals(base.plusDays(99).atStartOfDay(),
				GroupMilestones.hundredDaysAt(base, base.plusDays(99)).orElseThrow());
	}

	@Test
	void yearUsesFebruaryTwentyEighthInNonLeapYear() {
		var base = LocalDate.of(2024, 2, 29);
		assertTrue(GroupMilestones.oneYearAt(base, LocalDate.of(2025, 2, 27)).isEmpty());
		assertEquals(LocalDate.of(2025, 2, 28).atStartOfDay(),
				GroupMilestones.oneYearAt(base, LocalDate.of(2025, 2, 28)).orElseThrow());
		assertEquals(base.plusYears(1).atStartOfDay(),
				GroupMilestones.oneYearAt(base, LocalDate.of(2025, 3, 1)).orElseThrow());
	}
}
