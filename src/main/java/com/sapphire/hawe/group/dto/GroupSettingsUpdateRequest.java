package com.sapphire.hawe.group.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** All fields are optional; provided fields replace the corresponding settings. */
public record GroupSettingsUpdateRequest(
        @Size(max = 100) String name,
        @Pattern(regexp = "^(COUPLE|FAMILY|FRIEND|RIVAL|CASUAL)$") String relationType,
        @Min(1) Integer dailyMissionCount,
        @Valid List<@NotNull MissionSlotRequest> missionSlots
) {
}
