package com.sapphire.hawe.badge.dto;

import java.time.OffsetDateTime;

public record BadgeCatalogItem(String code, String name, String description, String tier,
		String imageKey, int target, int progress, boolean earned, OffsetDateTime earnedAt) {
}
