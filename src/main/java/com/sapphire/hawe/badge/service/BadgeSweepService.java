package com.sapphire.hawe.badge.service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import com.sapphire.hawe.badge.dao.UserActivityMapper;
import com.sapphire.hawe.global.config.BadgeProperties;

@Service
@EnableConfigurationProperties(BadgeProperties.class)
public class BadgeSweepService {
	private static final Logger log = LoggerFactory.getLogger(BadgeSweepService.class);
	private final UserActivityMapper activity;
	private final BadgeEvaluationService evaluation;
	private final BadgeProperties properties;
	private final AtomicBoolean running = new AtomicBoolean();

	public BadgeSweepService(UserActivityMapper activity, BadgeEvaluationService evaluation,
			BadgeProperties properties) {
		this.activity = activity;
		this.evaluation = evaluation;
		this.properties = properties;
	}

	public SweepResult sweep(LocalDateTime now) {
		if (!properties.enabled()) {
			log.debug("Badge sweep disabled");
			return new SweepResult(0, 0, Map.of());
		}
		if (!running.compareAndSet(false, true)) return new SweepResult(0, 0, Map.of());
		try {
			var today = now.toLocalDate();
			boolean includeMonth = today.getDayOfMonth() <= properties.perfectMonthDays();
			var candidates = new LinkedHashSet<Long>();
			candidates.addAll(activity.findRecentSubmitterIds(now.minusDays(
				Math.max(properties.lookbackDays(), properties.crowdWindowDays()))));
			candidates.addAll(activity.findMilestoneUserIds(today.minusDays(properties.lookbackDays()), today));
			if (includeMonth) {
				var previous = YearMonth.from(today.minusMonths(1));
				candidates.addAll(activity.findSubmitterIdsInRange(previous.atDay(1), previous.atEndOfMonth()));
			}
			var users = candidates.stream().sorted().toList();
			int evaluated = 0, failed = 0;
			Map<String, Integer> counts = new HashMap<>();
			for (int start = 0; start < users.size(); start += properties.batchSize()) {
				for (Long userId : users.subList(start, Math.min(users.size(), start + properties.batchSize()))) {
					try {
						var result = evaluation.evaluateAllBadges(userId, today, includeMonth, properties.dryRun());
						evaluated++;
						for (String code : result.awarded()) counts.merge(code, 1, Integer::sum);
					} catch (Exception error) {
						failed++;
						if (properties.dryRun()) log.warn("Badge dry-run evaluation failed");
						else log.warn("Badge evaluation failed for user {}", userId, error);
					}
				}
				log.debug("Badge sweep processed batch ending at {}", Math.min(users.size(), start + properties.batchSize()));
			}
			var result = new SweepResult(evaluated, failed, Map.copyOf(counts));
			log.info("Badge sweep {}: evaluated={}, failed={}, {}={}",
				properties.dryRun() ? "dry-run" : "award", evaluated, failed,
				properties.dryRun() ? "wouldAwardByCode" : "awardedByCode", result.awardedByCode());
			return result;
		} finally {
			running.set(false);
		}
	}
}
