package com.sapphire.hawe.group.dto;

import java.util.List;

public record GroupResponse(Long id, String name, Long ownerId, String relationType,
        int dailyMissionCount, List<MissionSlotResponse> missionSlots) {
}
