package com.sapphire.hawe.badge.dao;

import java.time.LocalDateTime;

public record FeaturedBadgeRow(Long userId, String featuredBadgeCode, String badgeCode,
		LocalDateTime earnedAt) {
}
