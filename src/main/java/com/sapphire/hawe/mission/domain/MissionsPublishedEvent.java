package com.sapphire.hawe.mission.domain;

import java.util.List;

public record MissionsPublishedEvent(Long groupId, List<Long> missionIds, List<Long> recipientUserIds) { }
