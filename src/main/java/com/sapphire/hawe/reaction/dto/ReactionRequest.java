package com.sapphire.hawe.reaction.dto;

import jakarta.validation.constraints.NotBlank;

public record ReactionRequest(@NotBlank String stickerType) { }
