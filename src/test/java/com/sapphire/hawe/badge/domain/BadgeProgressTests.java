package com.sapphire.hawe.badge.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class BadgeProgressTests {
	private static final LocalDateTime AT = LocalDateTime.of(2026, 1, 1, 12, 0);

	@Test
	void countsReuseEarlyBirdAndTemplateRulesAndLongestRun() {
		var templates = BadgeRules.REQUIRED_TEMPLATES.stream().sorted().toList();
		var submissions = IntStream.range(0, 11).mapToObj(i -> new ValidSubmission((long) i,
				AT.plusMinutes(i), i == 10, AT.plusMinutes(i).minusMinutes(i == 9 ? 61 : 60),
				i < 6 ? templates.get(i) : "DRAW_SKETCH")).toList();
		var days = IntStream.range(0, 8).filter(i -> i != 5)
				.mapToObj(i -> new DaySubmission(AT.toLocalDate().plusDays(i), AT.plusDays(i))).toList();
		var raw = BadgeProgress.raw(submissions, days, List.of(AT, AT), 19,
				List.of(new GroupBase(1L, AT.toLocalDate().minusDays(100), AT.toLocalDate(), false)),
				AT.toLocalDate());
		assertEquals(11, raw.get("SUBMISSIONS_10"));
		assertEquals(9, raw.get("EARLY_BIRD_10"));
		assertEquals(6, raw.get("ALL_TEMPLATES"));
		assertEquals(5, raw.get("STREAK_7"));
		assertEquals(19, raw.get("CROWD_FAVORITE"));
		assertEquals(101, raw.get("GROUP_100_DAYS"));
		assertEquals(2, raw.get("PHOTO_PERFECT_5"));
		assertEquals(0, raw.get("PERFECT_MONTH"));
	}
}
