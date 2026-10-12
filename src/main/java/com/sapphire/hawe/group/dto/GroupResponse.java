package com.sapphire.hawe.group.dto;

import java.util.List;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record GroupResponse(Long id, String name, Long ownerId, String relationType,
        OffsetDateTime createdAt, int memberCount, int maxMembers, String inviteCode, String inviteUrl,
        LocalDate startDate, int dailyMissionCount, List<MissionSlotResponse> missionSlots, long missionCount) {
}
