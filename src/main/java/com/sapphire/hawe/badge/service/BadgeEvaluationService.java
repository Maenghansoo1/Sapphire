package com.sapphire.hawe.badge.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.sapphire.hawe.badge.dao.BadgeMapper;
import com.sapphire.hawe.badge.dao.UserActivityMapper;
import com.sapphire.hawe.badge.domain.BadgeAwardedEvent;
import com.sapphire.hawe.badge.domain.BadgeRules;
import com.sapphire.hawe.badge.domain.UserBadge;

@Service
public class BadgeEvaluationService {
	private static final Logger log = LoggerFactory.getLogger(BadgeEvaluationService.class);
	private final UserActivityMapper activity;
	private final BadgeMapper badges;
	private final BadgeAwardService awards;
	private final ApplicationEventPublisher events;

	public BadgeEvaluationService(UserActivityMapper activity, BadgeMapper badges, BadgeAwardService awards,
			ApplicationEventPublisher events) {
		this.activity = activity;
		this.badges = badges;
		this.awards = awards;
		this.events = events;
	}

	public List<String> evaluateSubmissionBadges(Long userId) {
		var submissions = activity.findValidSubmissionsByUserId(userId);
		if (submissions.isEmpty()) return List.of();
		Set<String> owned = new HashSet<>(badges.findByUserId(userId).stream().map(UserBadge::badgeCode).toList());
		List<String> newlyAwarded = new ArrayList<>();
		for (var candidate : BadgeRules.evaluateSubmissionBadges(submissions)) {
			if (owned.contains(candidate.code())) continue;
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
