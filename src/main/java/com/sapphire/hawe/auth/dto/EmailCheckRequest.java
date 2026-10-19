package com.sapphire.hawe.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record EmailCheckRequest(@NotBlank @Email @Size(max = 255) String email) {
    public EmailCheckRequest {
        if (email != null) email = email.trim().toLowerCase(Locale.ROOT);
    }
}