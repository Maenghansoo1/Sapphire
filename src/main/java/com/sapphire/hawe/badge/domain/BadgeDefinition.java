package com.sapphire.hawe.badge.domain;

public record BadgeDefinition(String code, String nameKo, String descriptionKo, BadgeTier tier,
		String imageKey, int target, BadgeKind kind) {
}
