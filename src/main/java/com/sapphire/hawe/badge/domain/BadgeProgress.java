package com.sapphire.hawe.badge.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BadgeProgress {
	private BadgeProgress() {
	}

	public static Map<String, Integer> raw(List<ValidSubmission> submissions, List<DaySubmission> onTimeDays,
			List<java.time.LocalDateTime> perfectFitTimes, int crowdMaxInteractions,
			List<GroupBase> groups, LocalDate today) {
		Map<String, Integer> progress = new HashMap<>();
		int submitted = submissions.size();
		for (String code : List.of("FIRST_SUBMISSION", "SUBMISSIONS_10", "SUBMISSIONS_50", "SUBMISSIONS_100"))
			progress.put(code, submitted);
		progress.put("EARLY_BIRD_10", BadgeRules.earlyBirdCount(submissions));
		progress.put("ALL_TEMPLATES", (int) submissions.stream().map(ValidSubmission::template).distinct()
				.filter(BadgeRules.REQUIRED_TEMPLATES::contains).count());
		int longest = StreakCalculator.longestRun(onTimeDays);
		progress.put("STREAK_7", longest);
		progress.put("STREAK_30", longest);
		progress.put("PERFECT_MONTH", 0);
		progress.put("CROWD_FAVORITE", crowdMaxInteractions);
		int days = groups.stream().mapToInt(group -> (int) Math.max(0,
				ChronoUnit.DAYS.between(group.base(), today) + 1)).max().orElse(0);
		progress.put("GROUP_100_DAYS", days);
		progress.put("GROUP_1_YEAR", days);
		progress.put("PHOTO_PERFECT_5", perfectFitTimes.size());
		return Map.copyOf(progress);
	}
}
