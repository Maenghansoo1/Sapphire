package com.sapphire.hawe.badge.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BadgeRules {
	public static final Set<String> REQUIRED_TEMPLATES = Set.of(
		"PHOTO_NOW", "PHOTO_GALLERY", "PHOTO_COLOR", "DRAW_A_TO_B", "TEXT_ACROSTIC", "TEXT_FILL_BLANK");
	private static final Set<String> SUBMISSION_CODES = Set.of("FIRST_SUBMISSION", "SUBMISSIONS_10",
		"SUBMISSIONS_50", "SUBMISSIONS_100", "EARLY_BIRD_10", "ALL_TEMPLATES");
	private static final Set<String> SCHEDULED_CODES = Set.of("STREAK_7", "STREAK_30", "PERFECT_MONTH",
		"CROWD_FAVORITE", "GROUP_100_DAYS", "GROUP_1_YEAR", "PHOTO_PERFECT_5");

	private BadgeRules() {
	}

	public static List<EarnedBadge> evaluateSubmissionBadges(List<ValidSubmission> submissions) {
		List<ValidSubmission> ordered = submissions.stream()
			.sorted(Comparator.comparing(ValidSubmission::submittedAt)
				.thenComparing(ValidSubmission::submissionId)).toList();
		List<ValidSubmission> early = ordered.stream().filter(BadgeRules::isEarlyBird).toList();
		Map<String, LocalDateTime> firstByTemplate = new HashMap<>();
		for (ValidSubmission submission : ordered) {
			if (REQUIRED_TEMPLATES.contains(submission.template()))
				firstByTemplate.putIfAbsent(submission.template(), submission.submittedAt());
		}
		List<EarnedBadge> earned = new ArrayList<>();
		for (BadgeDefinition definition : BadgeCatalog.all()) {
			String code = definition.code();
			if (!SUBMISSION_CODES.contains(code)) continue;
			LocalDateTime earnedAt = null;
			if ("ALL_TEMPLATES".equals(code)) {
				if (firstByTemplate.keySet().containsAll(REQUIRED_TEMPLATES))
					earnedAt = firstByTemplate.values().stream().max(Comparator.naturalOrder()).orElseThrow();
			} else {
				List<ValidSubmission> candidates = "EARLY_BIRD_10".equals(code) ? early : ordered;
				if (candidates.size() >= definition.target())
					earnedAt = candidates.get(definition.target() - 1).submittedAt();
			}
			if (earnedAt != null) earned.add(new EarnedBadge(code, earnedAt));
		}
		return List.copyOf(earned);
	}

	public static int earlyBirdCount(List<ValidSubmission> submissions) {
		return (int) submissions.stream().filter(BadgeRules::isEarlyBird).count();
	}

	private static boolean isEarlyBird(ValidSubmission submission) {
		return !submission.late() && Duration.between(submission.publishedAt(), submission.submittedAt())
				.compareTo(Duration.ofHours(1)) <= 0;
	}

	public static List<EarnedBadge> evaluateScheduledBadges(ScheduledActivity activity, LocalDate today) {
		List<EarnedBadge> earned = new ArrayList<>();
		for (BadgeDefinition definition : BadgeCatalog.all()) {
			String code = definition.code();
			if (!SCHEDULED_CODES.contains(code)) continue;
			LocalDateTime earnedAt = switch (code) {
				case "STREAK_7", "STREAK_30" ->
					StreakCalculator.reachedAt(activity.onTimeDays(), definition.target()).orElse(null);
				case "PERFECT_MONTH" -> activity.perfectMonthEnd() != null && activity.perfectMonthRows().stream()
					.anyMatch(row -> row.openCount() == 0 && row.total() >= activity.perfectMonthMinMissions()
						&& row.submitted() == row.total() && row.memberCount() >= 2)
					? activity.perfectMonthEnd().plusDays(1).atStartOfDay() : null;
				case "GROUP_100_DAYS" -> activity.groups().stream()
					.flatMap(group -> GroupMilestones.hundredDaysAt(group.base(), today)
						.filter(at -> eligibleForMilestone(group, at)).stream())
					.min(Comparator.naturalOrder()).orElse(null);
				case "GROUP_1_YEAR" -> activity.groups().stream()
					.flatMap(group -> GroupMilestones.oneYearAt(group.base(), today)
						.filter(at -> eligibleForMilestone(group, at)).stream())
					.min(Comparator.naturalOrder()).orElse(null);
				case "PHOTO_PERFECT_5" -> activity.perfectFitTimes().size() >= definition.target()
					? activity.perfectFitTimes().stream().sorted().toList().get(definition.target() - 1) : null;
				default -> null;
			};
			if (earnedAt != null || "CROWD_FAVORITE".equals(code)
					&& activity.crowdMaxInteractions() >= definition.target())
				earned.add(new EarnedBadge(code, earnedAt));
		}
		return List.copyOf(earned);
	}

	private static boolean eligibleForMilestone(GroupBase group, LocalDateTime reachedAt) {
		return group.coupleStartDate() || !group.joinedOn().isAfter(reachedAt.toLocalDate());
	}
}
