package com.sapphire.hawe.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(@NotBlank @Size(min = 2, max = 10) String nickname) {
    public ProfileUpdateRequest { if (nickname != null) nickname = nickname.trim(); }
}
