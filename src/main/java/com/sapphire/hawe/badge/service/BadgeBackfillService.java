package com.sapphire.hawe.badge.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sapphire.hawe.badge.dao.UserActivityMapper;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.time.SeoulTime;

@Service
public class BadgeBackfillService {
	private static final Logger log = LoggerFactory.getLogger(BadgeBackfillService.class);
	private final UserActivityMapper activity;
	private final BadgeEvaluationService evaluation;
	private final boolean notify;
	private final AtomicBoolean running = new AtomicBoolean();

	public BadgeBackfillService(UserActivityMapper activity, BadgeEvaluationService evaluation,
			@Value("${hawe.badge.backfill.notify:false}") boolean notify) {
		this.activity = activity;
		this.evaluation = evaluation;
		this.notify = notify;
	}

	public BackfillResult run(int batch, long fromUserId, boolean dryRun) {
		if (!running.compareAndSet(false, true)) throw new BusinessException(BadgeErrorCode.BACKFILL_RUNNING);
		try {
			var userIds = activity.findUserIdsAfter(fromUserId, batch);
			long lastUserId = userIds.isEmpty() ? fromUserId : userIds.getLast();
			int failed = 0;
			Map<String, Integer> counts = new HashMap<>();
			var today = SeoulTime.today();
			for (Long userId : userIds) {
				try {
					var result = evaluation.evaluateForBackfill(userId, today, dryRun, notify);
					for (String code : result.awarded()) counts.merge(code, 1, Integer::sum);
				} catch (Exception error) {
					failed++;
					log.warn("Badge backfill failed for user {}", userId);
				}
			}
			var result = new BackfillResult(userIds.size(), failed, Map.copyOf(counts),
					lastUserId, userIds.size() < batch, dryRun);
			log.info("Badge backfill {}: processed={}, failed={}, awarded={}, lastUserId={}, done={}",
					dryRun ? "dry-run" : "award", result.processedUsers(), failed,
					result.awarded(), lastUserId, result.done());
			return result;
		} finally {
			running.set(false);
		}
	}
}
