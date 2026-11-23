package com.sapphire.hawe.badge.dto;

import java.time.OffsetDateTime;

public record MyBadgeResponse(String code, String name, String description, String tier,
		String imageKey, OffsetDateTime earnedAt, String status, String tokenId,
		String explorerUrl, boolean featured) {
}
