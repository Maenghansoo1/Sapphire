package com.sapphire.hawe.character.dto;

import jakarta.validation.constraints.NotNull;

public record CharacterUpdateRequest(@NotNull Long characterId) {
}
