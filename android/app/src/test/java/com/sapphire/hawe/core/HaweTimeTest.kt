package com.sapphire.hawe.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.util.TimeZone

class HaweTimeTest {
    @Test fun seoulNineBoundary() {
        val nine = LocalTime.of(9, 0)
        assertEquals(Instant.parse("2026-10-10T00:00:00Z"),
            HaweTime.window(nine, Instant.parse("2026-10-10T23:59:00Z")).first)
        assertEquals(Instant.parse("2026-10-11T00:00:00Z"),
            HaweTime.window(nine, Instant.parse("2026-10-11T00:00:00Z")).first)
    }

    @Test fun deviceTimezoneDoesNotChangeWindow() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val window = HaweTime.window(LocalTime.of(9, 0), Instant.parse("2026-10-11T23:59:00Z"))
            assertEquals(Instant.parse("2026-10-11T00:00:00Z"), window.first)
            assertEquals(Instant.parse("2026-10-12T00:00:00Z"), window.second)
        } finally { TimeZone.setDefault(original) }
    }
}
