package com.sapphire.hawe.badge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.sapphire.hawe.submission.domain.SubmissionRecordedEvent;

@Component
public class BadgeEventListener {
	private static final Logger log = LoggerFactory.getLogger(BadgeEventListener.class);
	private final BadgeEvaluationService evaluation;

	public BadgeEventListener(BadgeEvaluationService evaluation) {
		this.evaluation = evaluation;
	}

	@Async("badgeExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onSubmissionRecorded(SubmissionRecordedEvent event) {
		try {
			evaluation.evaluateSubmissionBadges(event.userId());
		} catch (Exception error) {
			log.warn("Badge evaluation failed for submission {}", event.submissionId(), error);
		}
	}
}
