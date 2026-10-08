package com.sapphire.hawe.poke.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PokeRequest(
        @NotNull Long toUserId,
        @NotBlank @Pattern(regexp = "poke|fling", message = "poke 또는 fling만 보낼 수 있습니다") String type) {
}
