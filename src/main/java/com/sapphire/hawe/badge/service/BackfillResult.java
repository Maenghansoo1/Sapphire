package com.sapphire.hawe.badge.service;

import java.util.Map;

public record BackfillResult(int processedUsers, int failedUsers, Map<String, Integer> awarded,
		long lastUserId, boolean done, boolean dryRun) {
}
