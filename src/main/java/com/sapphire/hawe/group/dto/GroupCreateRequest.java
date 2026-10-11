package com.sapphire.hawe.group.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GroupCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @Pattern(regexp = "^(COUPLE|FAMILY|FRIEND|RIVAL|CASUAL)$") String relationType,
        @Min(1) Integer dailyMissionCount,
        @Valid List<@NotNull MissionSlotRequest> missionSlots
) {
}
