package com.sapphire.hawe.badge.domain;

import java.time.LocalDateTime;

/** earnedAt is null when the source data has no reliable achievement timestamp. */
public record EarnedBadge(String code, LocalDateTime earnedAt) {
}
