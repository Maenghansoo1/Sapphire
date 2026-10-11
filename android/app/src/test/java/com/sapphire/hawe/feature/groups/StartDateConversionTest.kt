package com.sapphire.hawe.feature.groups

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import com.sapphire.hawe.core.HaweTime

class StartDateConversionTest {
    @Test fun utcDatePickerRoundTrip() {
        val date = LocalDate.of(2026, 10, 12)
        assertEquals(date, dateFromPickerMillis(datePickerMillis(date)))
        assertTrue(isSelectableDate(datePickerMillis(date), date))
        assertFalse(isSelectableDate(datePickerMillis(date.plusDays(1)), date))
    }

    @Test fun seoulMidnightBoundary() {
        val before = Instant.parse("2026-10-11T14:59:59Z").atZone(HaweTime.SEOUL).toLocalDate()
        val after = Instant.parse("2026-10-11T15:00:01Z").atZone(HaweTime.SEOUL).toLocalDate()
        assertEquals(LocalDate.of(2026, 10, 11), before)
        assertEquals(LocalDate.of(2026, 10, 12), after)
        assertFalse(isSelectableDate(datePickerMillis(after), before))
        assertTrue(isSelectableDate(datePickerMillis(after), after))
    }
}
