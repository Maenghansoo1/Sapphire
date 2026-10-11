package com.sapphire.hawe.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalTime;

@ConfigurationProperties(prefix = "hawe.mission")
public record MissionProperties(LocalTime defaultPublishTime, LocalTime defaultDeadlineTime, int maxDailyCount) {
}
