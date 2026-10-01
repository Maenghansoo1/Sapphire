package com.sapphire.hawe.group.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinRequest(@NotBlank String token) {
}
