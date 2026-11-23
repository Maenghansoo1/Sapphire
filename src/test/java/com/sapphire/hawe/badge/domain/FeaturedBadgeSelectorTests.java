package com.sapphire.hawe.badge.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeaturedBadgeSelectorTests {
	private static final LocalDateTime TIME = LocalDateTime.of(2026, 1, 1, 12, 0);

	private UserBadge badge(String code, LocalDateTime earnedAt) {
		return new UserBadge(1L, 1L, code, BadgeStatus.ISSUED_OFFCHAIN, earnedAt,
				null, null, null, null, TIME);
	}

	@Test
	void validSelectionWinsAndInvalidSelectionFallsBack() {
		var owned = List.of(badge("SUBMISSIONS_10", TIME), badge("SUBMISSIONS_100", TIME.minusDays(1)));
		assertEquals("SUBMISSIONS_10", FeaturedBadgeSelector.select("SUBMISSIONS_10", owned).orElseThrow());
		assertEquals("SUBMISSIONS_100", FeaturedBadgeSelector.select("STREAK_7", owned).orElseThrow());
		assertEquals("SUBMISSIONS_100", FeaturedBadgeSelector.select("REMOVED", owned).orElseThrow());
	}

	@Test
	void tierThenNewestThenCodeDecidesAndUnknownOwnedCodesAreIgnored() {
		var owned = List.of(badge("REMOVED", TIME.plusDays(2)), badge("STREAK_30", TIME),
				badge("SUBMISSIONS_100", TIME.minusYears(1)), badge("ALL_TEMPLATES", TIME.minusYears(1)));
		assertEquals("ALL_TEMPLATES", FeaturedBadgeSelector.select(null, owned).orElseThrow());
		assertEquals("STREAK_30", FeaturedBadgeSelector.select(null,
				List.of(badge("SUBMISSIONS_50", TIME.minusDays(1)), badge("STREAK_30", TIME))).orElseThrow());
		assertTrue(FeaturedBadgeSelector.select(null, List.of()).isEmpty());
		assertTrue(FeaturedBadgeSelector.select(null, List.of(badge("REMOVED", TIME))).isEmpty());
	}
}
