package com.sapphire.hawe.poke.dto;

import java.time.LocalDateTime;

public record PokeResponse(Long id, Long toUserId, String type, LocalDateTime sentAt) {
}
