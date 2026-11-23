package com.sapphire.hawe.badge.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sapphire.hawe.badge.dao.BadgeMapper;
import com.sapphire.hawe.badge.dao.FeaturedBadgeRow;
import com.sapphire.hawe.badge.dao.UserActivityMapper;
import com.sapphire.hawe.badge.domain.BadgeStatus;
import com.sapphire.hawe.badge.domain.UserBadge;
import com.sapphire.hawe.global.exception.BusinessException;

class BadgeQueryServiceTests {
	private final BadgeMapper badges = mock(BadgeMapper.class);
	private final UserActivityMapper activity = mock(UserActivityMapper.class);
	private final BadgeQueryService service = new BadgeQueryService(badges, activity);
	private final LocalDateTime at = LocalDateTime.of(2026, 1, 1, 12, 0);

	private UserBadge badge(String code, BadgeStatus status) {
		return new UserBadge(1L, 1L, code, status, at, "token-1", "secret-tx", 1, at, at);
	}

	@Test
	void myBadgesFilterRemovedCodesAndNormalizeStatus() {
		when(badges.findByUserId(1L)).thenReturn(List.of(badge("REMOVED", BadgeStatus.EARNED),
				badge("FIRST_SUBMISSION", BadgeStatus.ISSUED_OFFCHAIN),
				badge("SUBMISSIONS_10", BadgeStatus.EARNED),
				badge("STREAK_7", BadgeStatus.MINTING),
				badge("SUBMISSIONS_50", BadgeStatus.CONFIRMED),
				badge("SUBMISSIONS_100", BadgeStatus.FAILED)));
		when(badges.findFeaturedBadgeCode(1L)).thenReturn("FIRST_SUBMISSION");
		var rows = service.myBadges(1L);
		assertEquals(5, rows.size());
		assertEquals(List.of("RECORDING", "RECORDING", "RECORDING", "ON_CHAIN", "FAILED"),
				rows.stream().map(b -> b.status()).toList());
		assertEquals("token-1", rows.get(3).tokenId());
		assertTrue(rows.stream().filter(b -> b.featured()).allMatch(b -> b.code().equals("FIRST_SUBMISSION")));
		assertEquals(1, rows.stream().filter(b -> b.featured()).count());
		for (var row : rows) {
			assertNull(row.explorerUrl());
			if (!"ON_CHAIN".equals(row.status())) assertNull(row.tokenId());
		}
		assertEquals("+09:00", rows.getFirst().earnedAt().getOffset().toString());
	}

	@Test
	void catalogUsesSixQueriesAndClampsEarnedAndUnearnedProgress() {
		when(badges.findByUserId(1L)).thenReturn(List.of(badge("SUBMISSIONS_10", BadgeStatus.EARNED)));
		when(activity.findValidSubmissionsByUserId(1L)).thenReturn(java.util.stream.IntStream.range(0, 11)
				.mapToObj(i -> new com.sapphire.hawe.badge.domain.ValidSubmission((long) i,
						at.plusMinutes(i), false, at.plusMinutes(i).minusMinutes(10), "PHOTO_NOW")).toList());
		when(activity.findOnTimeSubmissionDays(1L)).thenReturn(List.of());
		when(activity.findPerfectFitTimes(1L)).thenReturn(List.of());
		when(activity.findMemberGroupBases(1L)).thenReturn(List.of());
		var rows = service.catalog(1L);
		assertEquals(13, rows.size());
		assertEquals(10, rows.stream().filter(b -> b.code().equals("SUBMISSIONS_10")).findFirst().orElseThrow().progress());
		assertEquals(0, rows.stream().filter(b -> b.code().equals("PERFECT_MONTH")).findFirst().orElseThrow().progress());
		for (var row : rows) {
			if (row.earned()) {
				assertEquals(row.target(), row.progress());
				assertNotNull(row.earnedAt());
			} else {
				assertTrue(row.progress() < row.target());
				assertTrue(row.progress() >= 0);
				assertNull(row.earnedAt());
			}
		}
		verify(badges).findByUserId(1L);
		verify(activity).findValidSubmissionsByUserId(1L);
		verify(activity).findOnTimeSubmissionDays(1L);
		verify(activity).findPerfectFitTimes(1L);
		verify(activity).findCrowdMaxInteractions(1L);
		verify(activity).findMemberGroupBases(1L);
	}

	@Test
	void featuredChoiceRequiresOwnershipAndNullResets() {
		when(badges.findByUserIdAndCode(1L, "STREAK_7")).thenReturn(badge("STREAK_7", BadgeStatus.EARNED));
		service.setFeatured(1L, "STREAK_7");
		service.setFeatured(1L, null);
		verify(badges).updateFeaturedBadge(1L, "STREAK_7");
		verify(badges).updateFeaturedBadge(1L, null);
		for (String code : List.of("FIRST_SUBMISSION", "UNKNOWN", "")) {
			assertEquals(BadgeErrorCode.BADGE_NOT_EARNED,
					assertThrows(BusinessException.class, () -> service.setFeatured(1L, code)).errorCode());
		}
		verify(badges, times(2)).updateFeaturedBadge(org.mockito.ArgumentMatchers.eq(1L),
				org.mockito.ArgumentMatchers.nullable(String.class));
	}

	@Test
	void featuredBatchSelectsOnceAndOmitsUsersWithoutBadges() {
		assertTrue(service.featuredBadges(List.of()).isEmpty());
		verify(badges, never()).findFeaturedBadgeRowsByUserIds(org.mockito.ArgumentMatchers.anyList());
		when(badges.findFeaturedBadgeRowsByUserIds(List.of(1L, 2L, 3L))).thenReturn(List.of(
				new FeaturedBadgeRow(1L, "FIRST_SUBMISSION", "FIRST_SUBMISSION", at),
				new FeaturedBadgeRow(1L, "FIRST_SUBMISSION", "SUBMISSIONS_100", at),
				new FeaturedBadgeRow(2L, null, "STREAK_7", at),
				new FeaturedBadgeRow(2L, null, "SUBMISSIONS_100", at.minusDays(1)),
				new FeaturedBadgeRow(3L, null, null, null)));
		var result = service.featuredBadges(List.of(1L, 2L, 3L));
		assertEquals("FIRST_SUBMISSION", result.get(1L).code());
		assertEquals("SUBMISSIONS_100", result.get(2L).code());
		assertFalse(result.containsKey(3L));
		verify(badges, times(1)).findFeaturedBadgeRowsByUserIds(List.of(1L, 2L, 3L));
	}
}
