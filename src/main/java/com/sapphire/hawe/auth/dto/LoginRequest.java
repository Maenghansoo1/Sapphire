package com.sapphire.hawe.auth.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Locale;

public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password
) {
    public LoginRequest {
        if (email != null) email = email.trim().toLowerCase(Locale.ROOT);
    }
}
