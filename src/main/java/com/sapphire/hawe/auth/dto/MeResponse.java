package com.sapphire.hawe.auth.dto;

import java.time.OffsetDateTime;

public record MeResponse(Long id, String email, String nickname, String characterKey,
        String profileImageUrl, OffsetDateTime createdAt) {
}
