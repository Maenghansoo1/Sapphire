package com.sapphire.hawe.poke.dto;

import java.time.OffsetDateTime;

public record PokeResponse(Long id, Long toUserId, String type, Long missionId, OffsetDateTime sentAt) {
}
