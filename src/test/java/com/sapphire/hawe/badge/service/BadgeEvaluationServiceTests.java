package com.sapphire.hawe.badge.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.sapphire.hawe.badge.dao.BadgeMapper;
import com.sapphire.hawe.badge.dao.UserActivityMapper;
import com.sapphire.hawe.badge.domain.BadgeAwardedEvent;
import com.sapphire.hawe.badge.domain.BadgeStatus;
import com.sapphire.hawe.badge.domain.UserBadge;
import com.sapphire.hawe.badge.domain.ValidSubmission;

class BadgeEvaluationServiceTests {
	private final UserActivityMapper activity = mock(UserActivityMapper.class);
	private final BadgeMapper badges = mock(BadgeMapper.class);
	private final BadgeAwardService awards = mock(BadgeAwardService.class);
	private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
	private final BadgeEvaluationService service = new BadgeEvaluationService(activity, badges, awards, events);
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
}
