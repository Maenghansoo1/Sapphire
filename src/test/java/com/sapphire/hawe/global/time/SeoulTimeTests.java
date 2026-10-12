package com.sapphire.hawe.global.time;

import com.sapphire.hawe.poke.dto.PokeResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeoulTimeTests {

    @Test
    void attachesSeoulOffsetEvenWhenJvmDefaultIsUtc() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            var value = SeoulTime.toOffset(LocalDateTime.of(2026, 9, 28, 9, 0));
            assertEquals("2026-09-28T09:00+09:00", value.toString());
            assertEquals(SeoulTime.ZONE.getRules().getOffset(value.toInstant()), value.getOffset());
            assertNull(SeoulTime.toOffset(null));
            assertEquals(ZonedDateTime.now(SeoulTime.ZONE).toLocalDate(), SeoulTime.today());
            assertTrue(Duration.between(SeoulTime.now(), ZonedDateTime.now(SeoulTime.ZONE).toLocalDateTime())
                    .abs().compareTo(Duration.ofSeconds(5)) < 0);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void serializesOffsetInResponse() {
        var response = new PokeResponse(1L, 2L, "poke", 10L,
                SeoulTime.toOffset(LocalDateTime.of(2026, 9, 28, 15, 41, 48)));
        String json = new ObjectMapper().writeValueAsString(response);
        assertTrue(json.contains("\"sentAt\":\"2026-09-28T15:41:48+09:00\""), json);
    }
}
