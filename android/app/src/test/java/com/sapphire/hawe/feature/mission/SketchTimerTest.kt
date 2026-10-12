package com.sapphire.hawe.feature.mission

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class SketchTimerTest {
    private class TestClock(var current: Instant) : Clock() {
        override fun instant(): Instant = current
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }

    @Test fun startsOnFirstStrokeAndUsesElapsedClockTime() {
        val clock = TestClock(Instant.EPOCH)
        val timer = SketchTimer(30, clock)
        clock.current = clock.current.plusSeconds(10)
        assertEquals(30, timer.remainingSeconds())
        assertFalse(timer.expired)
        timer.start()
        clock.current = clock.current.plusSeconds(7)
        timer.start()
        assertEquals(23, timer.remainingSeconds())
        clock.current = clock.current.plusSeconds(25)
        assertEquals(0, timer.remainingSeconds())
        assertTrue(timer.expired)
    }
}
