package com.sapphire.hawe.badge.domain;

import java.time.LocalDateTime;

public record EarnedBadge(String code, LocalDateTime earnedAt) {
}
