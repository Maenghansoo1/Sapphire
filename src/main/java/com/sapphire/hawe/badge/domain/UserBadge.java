package com.sapphire.hawe.badge.domain;

import java.time.LocalDateTime;

public record UserBadge(Long id, Long userId, String badgeCode, BadgeStatus status,
		LocalDateTime earnedAt, String tokenId, String txHash, Integer chainId,
		LocalDateTime confirmedAt, LocalDateTime createdAt) {
}
