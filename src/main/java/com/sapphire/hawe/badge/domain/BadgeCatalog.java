package com.sapphire.hawe.badge.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class BadgeCatalog {
	private static final List<BadgeDefinition> ALL = List.of(
		badge("FIRST_SUBMISSION", "첫 제출", "첫 번째 미션을 제출했어요.", BadgeTier.COMMON, 1, BadgeKind.EVENT),
		badge("SUBMISSIONS_10", "제출 10회", "미션을 10회 제출했어요.", BadgeTier.COMMON, 10, BadgeKind.EVENT),
		badge("SUBMISSIONS_50", "제출 50회", "미션을 50회 제출했어요.", BadgeTier.RARE, 50, BadgeKind.EVENT),
		badge("SUBMISSIONS_100", "제출 100회", "미션을 100회 제출했어요.", BadgeTier.EPIC, 100, BadgeKind.EVENT),
		badge("STREAK_7", "7일 연속", "7일 연속 제때 미션을 제출했어요.", BadgeTier.COMMON, 7, BadgeKind.SCHEDULED),
		badge("STREAK_30", "30일 연속", "30일 연속 제때 미션을 제출했어요.", BadgeTier.RARE, 30, BadgeKind.SCHEDULED),
		badge("PERFECT_MONTH", "한 달 개근", "한 달 동안 모든 미션을 제출했어요.", BadgeTier.EPIC, 1, BadgeKind.SCHEDULED),
		badge("EARLY_BIRD_10", "빠른 제출 10회", "발행 후 1시간 안에 10회 제출했어요.", BadgeTier.RARE, 10, BadgeKind.EVENT),
		badge("PHOTO_PERFECT_5", "찰떡 사진 5회", "찰떡 사진 배지를 5회 받았어요.", BadgeTier.RARE, 5, BadgeKind.EVENT),
		badge("CROWD_FAVORITE", "인기 게시물", "한 게시물에 반응과 댓글을 합쳐 20개를 받았어요.", BadgeTier.RARE, 20, BadgeKind.SCHEDULED),
		badge("GROUP_100_DAYS", "그룹 100일", "그룹이 100일을 맞았어요.", BadgeTier.RARE, 100, BadgeKind.SCHEDULED),
		badge("GROUP_1_YEAR", "그룹 1주년", "그룹이 1주년을 맞았어요.", BadgeTier.EPIC, 365, BadgeKind.SCHEDULED),
		badge("ALL_TEMPLATES", "모든 틀 도전", "서로 다른 미션 틀 6종을 모두 제출했어요.", BadgeTier.EPIC, 6, BadgeKind.EVENT)
	);
	private static final Map<String, BadgeDefinition> BY_CODE = ALL.stream()
		.collect(Collectors.toUnmodifiableMap(BadgeDefinition::code, Function.identity()));

	private BadgeCatalog() {
	}

	private static BadgeDefinition badge(String code, String name, String description, BadgeTier tier,
			int target, BadgeKind kind) {
		return new BadgeDefinition(code, name, description, tier, code.toLowerCase(java.util.Locale.ROOT), target, kind);
	}

	public static List<BadgeDefinition> all() {
		return ALL;
	}

	public static Optional<BadgeDefinition> byCode(String code) {
		return code == null ? Optional.empty() : Optional.ofNullable(BY_CODE.get(code));
	}
}
