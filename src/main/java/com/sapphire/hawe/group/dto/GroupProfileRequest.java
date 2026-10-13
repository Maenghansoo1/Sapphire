package com.sapphire.hawe.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GroupProfileRequest(@NotBlank @Size(min = 2, max = 10) String nickname,
        @NotNull Long characterId) {
    public GroupProfileRequest { if (nickname != null) nickname = nickname.trim(); }
}
