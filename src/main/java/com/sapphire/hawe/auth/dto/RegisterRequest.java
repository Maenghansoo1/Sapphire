package com.sapphire.hawe.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 100) @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$") String password,
        @NotBlank @Size(min = 2, max = 10) String nickname,
        @AssertTrue boolean agreeTerms,
        @AssertTrue boolean agreePrivacy
) {
    public RegisterRequest {
        if (email != null) email = email.trim().toLowerCase(Locale.ROOT);
        if (nickname != null) nickname = nickname.trim();
    }
}
