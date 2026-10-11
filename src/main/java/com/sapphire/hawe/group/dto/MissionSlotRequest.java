package com.sapphire.hawe.group.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record MissionSlotRequest(
        @NotNull Integer slotNo,
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String publishTime,
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String deadlineTime
) {
}
