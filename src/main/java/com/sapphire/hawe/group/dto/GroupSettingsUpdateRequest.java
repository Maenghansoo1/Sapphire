package com.sapphire.hawe.group.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** All fields optional — only the ones present in the request body are applied (partial update). */
public record GroupSettingsUpdateRequest(
        @Size(max = 100) String name,
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "HH:mm 형식이어야 합니다") String missionPublishTime,
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "HH:mm 형식이어야 합니다") String missionDeadlineTime
) {
}
