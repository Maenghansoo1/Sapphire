package com.sapphire.hawe.badge.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class FeaturedBadgeSelector {
	private FeaturedBadgeSelector() {
	}

	public static Optional<String> select(String selectedCodeOrNull, List<UserBadge> owned) {
		if (selectedCodeOrNull != null && BadgeCatalog.byCode(selectedCodeOrNull).isPresent()
				&& owned.stream().anyMatch(b -> selectedCodeOrNull.equals(b.badgeCode()))) {
			return Optional.of(selectedCodeOrNull);
		}
		return owned.stream()
			.filter(b -> BadgeCatalog.byCode(b.badgeCode()).isPresent())
			.sorted(Comparator.<UserBadge>comparingInt(b -> BadgeCatalog.byCode(b.badgeCode()).orElseThrow().tier().ordinal())
				.reversed().thenComparing(UserBadge::earnedAt, Comparator.reverseOrder())
				.thenComparing(UserBadge::badgeCode))
			.map(UserBadge::badgeCode).findFirst();
	}
}
