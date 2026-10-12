package com.sapphire.hawe.group.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.time.LocalDate;

/** All fields are optional; provided fields replace the corresponding settings. */
public record GroupSettingsUpdateRequest(
        @Size(min = 1, max = 16) String name,
        @Pattern(regexp = "^(COUPLE|FAMILY|FRIEND|RIVAL|CASUAL)$") String relationType,
        LocalDate startDate,
        @Min(1) Integer dailyMissionCount,
        @Valid List<@NotNull MissionSlotRequest> missionSlots
) {
    public GroupSettingsUpdateRequest {
        if (name != null) name = name.trim();
    }
}
