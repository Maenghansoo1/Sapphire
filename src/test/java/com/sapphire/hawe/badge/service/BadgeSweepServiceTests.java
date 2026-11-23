package com.sapphire.hawe.badge.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.sapphire.hawe.badge.dao.UserActivityMapper;
import com.sapphire.hawe.global.config.BadgeProperties;

class BadgeSweepServiceTests {
	private final UserActivityMapper activity = mock(UserActivityMapper.class);
	private final BadgeEvaluationService evaluation = mock(BadgeEvaluationService.class);
	private final LocalDateTime now = LocalDateTime.of(2026, 4, 1, 3, 30);

	private BadgeSweepService service(boolean enabled, boolean dryRun, int batchSize) {
		return new BadgeSweepService(activity, evaluation,
				new BadgeProperties(enabled, dryRun, 2, 14, 3, 5, batchSize));
	}

	@Test
	void disabledDoesNoQueries() {
		assertEquals(new SweepResult(0, 0, Map.of()), service(false, false, 2).sweep(now));
		verifyNoInteractions(activity, evaluation);
	}

	@Test
	void deduplicatesCandidatesBatchesAndContinuesAfterFailure() {
		when(activity.findRecentSubmitterIds(now.minusDays(14))).thenReturn(List.of(3L, 1L));
		when(activity.findMilestoneUserIds(now.toLocalDate().minusDays(2), now.toLocalDate()))
			.thenReturn(List.of(1L, 2L));
		when(activity.findSubmitterIdsInRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
			.thenReturn(List.of(2L, 4L));
		when(evaluation.evaluateAllBadges(1L, now.toLocalDate(), true, false))
			.thenReturn(new EvaluationResult(List.of("FIRST_SUBMISSION")));
		when(evaluation.evaluateAllBadges(2L, now.toLocalDate(), true, false))
			.thenThrow(new IllegalStateException("failure"));
		when(evaluation.evaluateAllBadges(3L, now.toLocalDate(), true, false))
			.thenReturn(new EvaluationResult(List.of("STREAK_7")));
		when(evaluation.evaluateAllBadges(4L, now.toLocalDate(), true, false))
			.thenReturn(new EvaluationResult(List.of()));
		assertEquals(new SweepResult(3, 1, Map.of("FIRST_SUBMISSION", 1, "STREAK_7", 1)),
				service(true, false, 2).sweep(now));
		verify(evaluation, times(4)).evaluateAllBadges(anyLong(), eq(now.toLocalDate()), eq(true), eq(false));
	}

	@Test
	void dryRunAndMonthWindowArePassedThrough() {
		var later = now.plusDays(3);
		when(activity.findRecentSubmitterIds(later.minusDays(14))).thenReturn(List.of(1L));
		when(evaluation.evaluateAllBadges(1L, later.toLocalDate(), false, true))
			.thenReturn(new EvaluationResult(List.of("CROWD_FAVORITE")));
		assertEquals(Map.of("CROWD_FAVORITE", 1), service(true, true, 1).sweep(later).awardedByCode());
		verify(activity, never()).findSubmitterIdsInRange(any(), any());
		verify(evaluation).evaluateAllBadges(1L, later.toLocalDate(), false, true);
	}

	@Test
	void overlappingRunReturnsWithoutEvaluatingAgain() throws Exception {
		when(activity.findRecentSubmitterIds(now.minusDays(14))).thenReturn(List.of(1L));
		CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
		when(evaluation.evaluateAllBadges(1L, now.toLocalDate(), true, false)).thenAnswer(invocation -> {
			entered.countDown();
			release.await(5, TimeUnit.SECONDS);
			return new EvaluationResult(List.of());
		});
		BadgeSweepService sweep = service(true, false, 1);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread first = new Thread(() -> {
			try { sweep.sweep(now); } catch (Throwable error) { failure.set(error); }
		});
		first.start();
		try {
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			assertEquals(new SweepResult(0, 0, Map.of()), sweep.sweep(now));
		} finally {
			release.countDown();
			first.join(5000);
		}
		assertNull(failure.get());
		verify(evaluation, times(1)).evaluateAllBadges(1L, now.toLocalDate(), true, false);
	}
}
