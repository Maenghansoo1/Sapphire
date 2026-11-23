package com.sapphire.hawe.badge.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.sapphire.hawe.badge.dao.UserActivityMapper;
import com.sapphire.hawe.global.exception.BusinessException;

class BadgeBackfillServiceTests {
	private final UserActivityMapper activity = mock(UserActivityMapper.class);
	private final BadgeEvaluationService evaluation = mock(BadgeEvaluationService.class);

	@Test
	void batchesCountAwardsAndContinuePastFailedUsers() {
		var service = new BadgeBackfillService(activity, evaluation, false);
		when(activity.findUserIdsAfter(7, 3)).thenReturn(List.of(8L, 9L, 10L));
		when(evaluation.evaluateForBackfill(eq(8L), any(), eq(true), eq(false)))
			.thenReturn(new EvaluationResult(List.of("FIRST_SUBMISSION", "STREAK_7")));
		when(evaluation.evaluateForBackfill(eq(9L), any(), eq(true), eq(false)))
			.thenThrow(new IllegalStateException("failed"));
		when(evaluation.evaluateForBackfill(eq(10L), any(), eq(true), eq(false)))
			.thenReturn(new EvaluationResult(List.of("FIRST_SUBMISSION")));
		assertEquals(new BackfillResult(3, 1, Map.of("FIRST_SUBMISSION", 2, "STREAK_7", 1),
				10, false, true), service.run(3, 7, true));
		verify(evaluation).evaluateForBackfill(eq(10L), any(), eq(true), eq(false));
		when(activity.findUserIdsAfter(10, 3)).thenReturn(List.of());
		assertEquals(new BackfillResult(0, 0, Map.of(), 10, true, false), service.run(3, 10, false));
	}

	@Test
	void notificationSettingIsPassedToEvaluation() {
		var service = new BadgeBackfillService(activity, evaluation, true);
		when(activity.findUserIdsAfter(0, 2)).thenReturn(List.of(1L));
		when(evaluation.evaluateForBackfill(eq(1L), any(), eq(false), eq(true)))
			.thenReturn(new EvaluationResult(List.of()));
		assertTrue(service.run(2, 0, false).done());
		verify(evaluation).evaluateForBackfill(eq(1L), any(), eq(false), eq(true));
	}

	@Test
	void concurrentRunReturnsConflict() throws Exception {
		var service = new BadgeBackfillService(activity, evaluation, false);
		var entered = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		when(activity.findUserIdsAfter(0, 1)).thenAnswer(invocation -> {
			entered.countDown();
			if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timed out");
			return List.of();
		});
		try (var pool = Executors.newSingleThreadExecutor()) {
			var first = pool.submit(() -> service.run(1, 0, true));
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			var error = assertThrows(BusinessException.class, () -> service.run(1, 0, true));
			assertEquals(BadgeErrorCode.BACKFILL_RUNNING, error.errorCode());
			release.countDown();
			assertTrue(first.get(5, TimeUnit.SECONDS).done());
		} finally {
			release.countDown();
		}
	}
}
