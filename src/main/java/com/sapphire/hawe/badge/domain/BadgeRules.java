package com.sapphire.hawe.badge.domain;

import java.time.Duration;
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

	private BadgeRules() {
	}

	public static List<EarnedBadge> evaluateSubmissionBadges(List<ValidSubmission> submissions) {
		List<ValidSubmission> ordered = submissions.stream()
			.sorted(Comparator.comparing(ValidSubmission::submittedAt)
				.thenComparing(ValidSubmission::submissionId)).toList();
		List<ValidSubmission> early = ordered.stream().filter(b -> !b.late()
				&& Duration.between(b.publishedAt(), b.submittedAt()).compareTo(Duration.ofHours(1)) <= 0).toList();
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
}
