package com.sapphire.hawe.badge.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.time.LocalDate;
import java.time.YearMonth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.sapphire.hawe.badge.dao.BadgeMapper;
import com.sapphire.hawe.badge.dao.UserActivityMapper;
import com.sapphire.hawe.badge.domain.BadgeAwardedEvent;
import com.sapphire.hawe.badge.domain.BadgeRules;
import com.sapphire.hawe.badge.domain.EarnedBadge;
import com.sapphire.hawe.badge.domain.ScheduledActivity;
import com.sapphire.hawe.badge.domain.UserBadge;
import com.sapphire.hawe.global.config.BadgeProperties;

@Service
public class BadgeEvaluationService {
	private static final Logger log = LoggerFactory.getLogger(BadgeEvaluationService.class);
	private final UserActivityMapper activity;
	private final BadgeMapper badges;
	private final BadgeAwardService awards;
	private final ApplicationEventPublisher events;
	private final BadgeProperties properties;

	public BadgeEvaluationService(UserActivityMapper activity, BadgeMapper badges, BadgeAwardService awards,
			ApplicationEventPublisher events, BadgeProperties properties) {
		this.activity = activity;
		this.badges = badges;
		this.awards = awards;
		this.events = events;
		this.properties = properties;
	}

	public List<String> evaluateSubmissionBadges(Long userId) {
		var submissions = activity.findValidSubmissionsByUserId(userId);
		if (submissions.isEmpty()) return List.of();
		return awardCandidates(userId, BadgeRules.evaluateSubmissionBadges(submissions), false);
	}

	public EvaluationResult evaluateAllBadges(Long userId, LocalDate today, boolean includePerfectMonth, boolean dryRun) {
		var candidates = new ArrayList<>(BadgeRules.evaluateSubmissionBadges(
				activity.findValidSubmissionsByUserId(userId)));
		var previousMonth = YearMonth.from(today.minusMonths(1));
		var monthRows = includePerfectMonth
				? activity.findPerfectMonthRows(userId, previousMonth.atDay(1), previousMonth.atEndOfMonth())
				: List.<com.sapphire.hawe.badge.domain.PerfectMonthRow>of();
		var scheduled = new ScheduledActivity(activity.findOnTimeSubmissionDays(userId),
				activity.findPerfectFitTimes(userId), activity.findCrowdMaxInteractions(userId),
				activity.findMemberGroupBases(userId), monthRows,
				includePerfectMonth ? previousMonth.atEndOfMonth() : null,
				properties.perfectMonthMinMissions());
		candidates.addAll(BadgeRules.evaluateScheduledBadges(scheduled, today));
		return new EvaluationResult(awardCandidates(userId, candidates, dryRun));
	}

	private List<String> awardCandidates(Long userId, List<EarnedBadge> candidates, boolean dryRun) {
		if (candidates.isEmpty()) return List.of();
		Set<String> owned = new HashSet<>(badges.findByUserId(userId).stream().map(UserBadge::badgeCode).toList());
		List<String> newlyAwarded = new ArrayList<>();
		for (var candidate : candidates) {
			if (owned.contains(candidate.code())) continue;
			if (dryRun) {
				newlyAwarded.add(candidate.code());
				continue;
			}
			try {
				if (awards.award(userId, candidate.code(), candidate.earnedAt())) {
					newlyAwarded.add(candidate.code());
					events.publishEvent(new BadgeAwardedEvent(userId, candidate.code()));
				}
			} catch (Exception error) {
				log.warn("Badge award failed for user {} code {}", userId, candidate.code(), error);
			}
		}
		return List.copyOf(newlyAwarded);
	}
}
