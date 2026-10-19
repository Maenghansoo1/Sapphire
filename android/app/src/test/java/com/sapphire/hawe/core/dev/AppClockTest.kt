package com.sapphire.hawe.core.dev

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class AppClockTest {
    @Test fun offsetMovesClockAndDisableRestoresRealTime() {
        DevMode.changeEnabled(true)
        try {
            DevMode.setOffset(0)
            assertTrue(Duration.between(Instant.now(), AppClock.now()).abs().seconds < 2)
            DevMode.setOffset(86400)
            assertTrue(Duration.between(Instant.now().plusSeconds(86400), AppClock.now()).abs().seconds < 2)
            assertEquals(AppClock.now().atZone(com.sapphire.hawe.core.HaweTime.SEOUL).toLocalDate(), AppClock.today())
        } finally { DevMode.changeEnabled(false) }
        assertEquals(0, DevMode.offsetSeconds)
    }

    @Test fun todayUsesSeoulDateAcrossMidnight() {
        DevMode.changeEnabled(true)
        try {
            val target = LocalDate.of(2030, 1, 1).atTime(0, 1)
                .atZone(com.sapphire.hawe.core.HaweTime.SEOUL).toInstant()
            DevMode.setOffset(Duration.between(Instant.now(), target).seconds)
            assertEquals(LocalDate.of(2030, 1, 1), AppClock.today())
        } finally { DevMode.changeEnabled(false) }
    }
}
