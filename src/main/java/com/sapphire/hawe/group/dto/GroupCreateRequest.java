package com.sapphire.hawe.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GroupCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "HH:mm 형식이어야 합니다") String missionPublishTime,
        @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "HH:mm 형식이어야 합니다") String missionDeadlineTime
) {
}
