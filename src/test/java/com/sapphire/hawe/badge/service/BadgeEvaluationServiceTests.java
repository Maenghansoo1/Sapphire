package com.sapphire.hawe.badge.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.sapphire.hawe.badge.dao.BadgeMapper;
import com.sapphire.hawe.badge.dao.UserActivityMapper;
import com.sapphire.hawe.badge.domain.BadgeAwardedEvent;
import com.sapphire.hawe.badge.domain.BadgeStatus;
import com.sapphire.hawe.badge.domain.UserBadge;
import com.sapphire.hawe.badge.domain.ValidSubmission;
import com.sapphire.hawe.badge.domain.GroupBase;
import com.sapphire.hawe.badge.domain.PerfectMonthRow;
import com.sapphire.hawe.global.config.BadgeProperties;

class BadgeEvaluationServiceTests {
	private final UserActivityMapper activity = mock(UserActivityMapper.class);
	private final BadgeMapper badges = mock(BadgeMapper.class);
	private final BadgeAwardService awards = mock(BadgeAwardService.class);
	private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
	private final BadgeEvaluationService service = new BadgeEvaluationService(activity, badges, awards, events,
			new BadgeProperties(false, false, null, null, null, null, null));
	private final LocalDateTime time = LocalDateTime.of(2026, 1, 1, 12, 0);

	private List<ValidSubmission> tenSubmissions() {
		return java.util.stream.IntStream.rangeClosed(1, 10).mapToObj(i ->
			new ValidSubmission((long) i, time.plusMinutes(i), false, time.plusMinutes(i).minusMinutes(10), "PHOTO_NOW"))
			.toList();
	}

	@Test
	void ownedCodesAreSkippedAndOnlyNewAwardsPublishEvents() {
		when(activity.findValidSubmissionsByUserId(1L)).thenReturn(tenSubmissions());
		when(badges.findByUserId(1L)).thenReturn(List.of(new UserBadge(1L, 1L, "FIRST_SUBMISSION",
			BadgeStatus.EARNED, time, null, null, null, null, time)));
		when(awards.award(1L, "SUBMISSIONS_10", time.plusMinutes(10))).thenReturn(true);
		when(awards.award(1L, "EARLY_BIRD_10", time.plusMinutes(10))).thenReturn(false);
		assertEquals(List.of("SUBMISSIONS_10"), service.evaluateSubmissionBadges(1L));
		verify(awards, never()).award(1L, "FIRST_SUBMISSION", time.plusMinutes(1));
		verify(events).publishEvent(new BadgeAwardedEvent(1L, "SUBMISSIONS_10"));
		verifyNoMoreInteractions(events);
	}

	@Test
	void oneAwardFailureDoesNotStopLaterCandidate() {
		when(activity.findValidSubmissionsByUserId(1L)).thenReturn(tenSubmissions());
		when(badges.findByUserId(1L)).thenReturn(List.of());
		when(awards.award(1L, "FIRST_SUBMISSION", time.plusMinutes(1)))
			.thenThrow(new IllegalStateException("fail"));
		when(awards.award(1L, "SUBMISSIONS_10", time.plusMinutes(10))).thenReturn(true);
		assertEquals(List.of("SUBMISSIONS_10"), service.evaluateSubmissionBadges(1L));
		verify(events).publishEvent(new BadgeAwardedEvent(1L, "SUBMISSIONS_10"));
	}

	@Test
	void noSubmissionsMeansNoWrites() {
		when(activity.findValidSubmissionsByUserId(1L)).thenReturn(List.of());
		assertEquals(List.of(), service.evaluateSubmissionBadges(1L));
		verifyNoInteractions(badges, awards, events);
	}

	private void scheduledInputs() {
		when(activity.findValidSubmissionsByUserId(1L)).thenReturn(tenSubmissions());
		when(activity.findOnTimeSubmissionDays(1L)).thenReturn(List.of());
		when(activity.findPerfectFitTimes(1L)).thenReturn(List.of());
		when(activity.findMemberGroupBases(1L)).thenReturn(List.of(new GroupBase(2L,
				LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2), false)));
		when(activity.findPerfectMonthRows(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
				.thenReturn(List.of(new PerfectMonthRow(2L, 5, 5, 0, 2)));
		when(badges.findByUserId(1L)).thenReturn(List.of());
	}

	@Test
	void allRulesIncludeSubmissionAndScheduledAndDryRunDoesNotWrite() {
		scheduledInputs();
		var codes = service.evaluateAllBadges(1L, LocalDate.of(2026, 4, 1), true, true).awarded();
		assertEquals(List.of("FIRST_SUBMISSION", "SUBMISSIONS_10", "EARLY_BIRD_10",
				"PERFECT_MONTH", "GROUP_100_DAYS", "GROUP_1_YEAR"), codes);
		verifyNoInteractions(awards, events);
		when(awards.award(1L, "FIRST_SUBMISSION", time.plusMinutes(1))).thenReturn(true);
		assertEquals(List.of("FIRST_SUBMISSION"), service.evaluateAllBadges(1L,
				LocalDate.of(2026, 4, 1), true, false).awarded());
		verify(events).publishEvent(new BadgeAwardedEvent(1L, "FIRST_SUBMISSION"));
	}

	@Test
	void monthCanBeSkippedAndOwnedCodesAreExcluded() {
		scheduledInputs();
		when(badges.findByUserId(1L)).thenReturn(List.of(new UserBadge(1L, 1L, "FIRST_SUBMISSION",
			BadgeStatus.EARNED, time, null, null, null, null, time)));
		var codes = service.evaluateAllBadges(1L, LocalDate.of(2026, 4, 4), false, true).awarded();
		assertFalse(codes.contains("FIRST_SUBMISSION"));
		assertFalse(codes.contains("PERFECT_MONTH"));
		verify(activity, never()).findPerfectMonthRows(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
	}
}
