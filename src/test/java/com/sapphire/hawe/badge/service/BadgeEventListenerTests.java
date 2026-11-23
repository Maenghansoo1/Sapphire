package com.sapphire.hawe.badge.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import com.sapphire.hawe.submission.domain.SubmissionRecordedEvent;

class BadgeEventListenerTests {
	@Test
	void delegatesAndSwallowsEvaluationFailure() {
		BadgeEvaluationService evaluation = mock(BadgeEvaluationService.class);
		BadgeEventListener listener = new BadgeEventListener(evaluation);
		var event = new SubmissionRecordedEvent(9L, 3L);
		listener.onSubmissionRecorded(event);
		verify(evaluation).evaluateSubmissionBadges(3L);
		doThrow(new IllegalStateException("failure")).when(evaluation).evaluateSubmissionBadges(3L);
		assertDoesNotThrow(() -> listener.onSubmissionRecorded(event));
		verify(evaluation, times(2)).evaluateSubmissionBadges(3L);
	}
}
