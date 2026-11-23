package com.sapphire.hawe.badge.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.sapphire.hawe.mission.domain.MissionTemplate;

class BadgeRulesTests {
	private static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 12, 0);

	private ValidSubmission submission(int id, String template, boolean late, int minutesAfterPublish) {
		var submitted = START.plusMinutes(id);
		return new ValidSubmission((long) id, submitted, late, submitted.minusMinutes(minutesAfterPublish), template);
	}

	private List<ValidSubmission> count(int n) {
		return IntStream.rangeClosed(1, n).mapToObj(i -> submission(i, "PHOTO_NOW", false, 10)).toList();
	}

	private EarnedBadge find(List<EarnedBadge> badges, String code) {
		return badges.stream().filter(b -> b.code().equals(code)).findFirst().orElse(null);
	}

	@Test
	void firstAndCountBoundariesUseNthSubmissionTimeDespiteInputOrder() {
		assertTrue(BadgeRules.evaluateSubmissionBadges(List.of()).isEmpty());
		assertEquals(START.plusMinutes(1), find(BadgeRules.evaluateSubmissionBadges(count(1)), "FIRST_SUBMISSION").earnedAt());
		assertNull(find(BadgeRules.evaluateSubmissionBadges(count(9)), "SUBMISSIONS_10"));
		var shuffled = new ArrayList<>(count(10));
		Collections.reverse(shuffled);
		assertEquals(START.plusMinutes(10), find(BadgeRules.evaluateSubmissionBadges(shuffled), "SUBMISSIONS_10").earnedAt());
		assertNull(find(BadgeRules.evaluateSubmissionBadges(count(49)), "SUBMISSIONS_50"));
		assertEquals(START.plusMinutes(50), find(BadgeRules.evaluateSubmissionBadges(count(50)), "SUBMISSIONS_50").earnedAt());
		assertNull(find(BadgeRules.evaluateSubmissionBadges(count(99)), "SUBMISSIONS_100"));
		assertEquals(START.plusMinutes(100), find(BadgeRules.evaluateSubmissionBadges(count(100)), "SUBMISSIONS_100").earnedAt());
	}

	@Test
	void equalTimesUseSubmissionIdAndEarlyBirdUsesInclusiveHour() {
		var early = new ArrayList<ValidSubmission>();
		for (int i = 1; i <= 9; i++) early.add(submission(i, "PHOTO_NOW", false, 60));
		var justOverHour = submission(10, "PHOTO_NOW", false, 60);
		early.add(new ValidSubmission(justOverHour.submissionId(), justOverHour.submittedAt(), false,
				justOverHour.publishedAt().minusSeconds(1), justOverHour.template()));
		early.add(submission(11, "PHOTO_NOW", true, 1));
		assertNull(find(BadgeRules.evaluateSubmissionBadges(early), "EARLY_BIRD_10"));
		early.add(submission(12, "PHOTO_NOW", false, 60));
		assertEquals(START.plusMinutes(12), find(BadgeRules.evaluateSubmissionBadges(early), "EARLY_BIRD_10").earnedAt());
		var beforePublication = new ValidSubmission(13L, START.plusMinutes(13), false,
				START.plusMinutes(14), "PHOTO_NOW");
		var ninePlusEarlyClockSkew = new ArrayList<>(count(9));
		ninePlusEarlyClockSkew.add(beforePublication);
		assertEquals(beforePublication.submittedAt(),
				find(BadgeRules.evaluateSubmissionBadges(ninePlusEarlyClockSkew), "EARLY_BIRD_10").earnedAt());
		var sameTime = new ArrayList<>(count(10));
		sameTime.replaceAll(s -> new ValidSubmission(s.submissionId(), START, false, START.minusHours(1), s.template()));
		Collections.reverse(sameTime);
		assertEquals(START, find(BadgeRules.evaluateSubmissionBadges(sameTime), "SUBMISSIONS_10").earnedAt());
	}

	@Test
	void allTemplatesUsesLatestFirstSubmissionAndIgnoresOtherTemplates() {
		assertEquals(6, BadgeRules.REQUIRED_TEMPLATES.size());
		assertEquals(BadgeCatalog.byCode("ALL_TEMPLATES").orElseThrow().target(), BadgeRules.REQUIRED_TEMPLATES.size());
		assertFalse(BadgeRules.REQUIRED_TEMPLATES.contains("DRAW_SKETCH"));
		for (String name : BadgeRules.REQUIRED_TEMPLATES) assertDoesNotThrow(() -> MissionTemplate.valueOf(name));
		var required = new ArrayList<>(BadgeRules.REQUIRED_TEMPLATES.stream().sorted().toList());
		var submissions = new ArrayList<ValidSubmission>();
		for (int i = 0; i < 5; i++) submissions.add(submission(i + 1, required.get(i), false, 10));
		submissions.add(submission(6, "DRAW_SKETCH", false, 10));
		assertNull(find(BadgeRules.evaluateSubmissionBadges(submissions), "ALL_TEMPLATES"));
		submissions.add(submission(7, required.get(5), false, 10));
		submissions.add(submission(8, required.get(5), false, 10));
		assertEquals(START.plusMinutes(7), find(BadgeRules.evaluateSubmissionBadges(submissions), "ALL_TEMPLATES").earnedAt());
		var result = BadgeRules.evaluateSubmissionBadges(submissions);
		assertEquals(List.of("FIRST_SUBMISSION", "ALL_TEMPLATES"), result.stream().map(EarnedBadge::code).toList());
		assertEquals(result.size(), Set.copyOf(result.stream().map(EarnedBadge::code).toList()).size());
	}
}
