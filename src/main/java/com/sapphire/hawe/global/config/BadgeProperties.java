package com.sapphire.hawe.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "hawe.badge")
public record BadgeProperties(@DefaultValue("false") boolean enabled, @DefaultValue("false") boolean dryRun,
		Integer lookbackDays, Integer crowdWindowDays, Integer perfectMonthDays,
		Integer perfectMonthMinMissions, Integer batchSize) {
	public BadgeProperties {
		lookbackDays = lookbackDays == null ? 2 : lookbackDays;
		crowdWindowDays = crowdWindowDays == null ? 14 : crowdWindowDays;
		perfectMonthDays = perfectMonthDays == null ? 3 : perfectMonthDays;
		perfectMonthMinMissions = perfectMonthMinMissions == null ? 5 : perfectMonthMinMissions;
		batchSize = batchSize == null ? 500 : batchSize;
		if (lookbackDays < 1 || crowdWindowDays < 1 || perfectMonthDays < 1 || perfectMonthDays > 28
				|| perfectMonthMinMissions < 1 || batchSize < 1)
			throw new IllegalArgumentException("Invalid badge properties");
	}
}
