package com.sapphire.hawe.feature.insight

import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class InsightFormatTest {
    @Test fun boundsTwelveMonths() {
        val current = YearMonth.of(2026, 10)
        assertFalse(canShowPreviousInsight(current.minusMonths(12), current))
        assertTrue(canShowPreviousInsight(current.minusMonths(11), current))
        assertFalse(canShowNextInsight(current, current))
        assertTrue(canShowNextInsight(current.minusMonths(1), current))
    }
    @Test fun formatsRatesAndDelta() {
        assertEquals(75, insightPercent(0.745))
        assertEquals(0, insightPercent(-1.0))
        assertEquals(100, insightPercent(2.0))
        assertEquals("+5", insightDelta(5))
        assertEquals("-3", insightDelta(-3))
        assertEquals("변화 없음", insightDelta(0))
        assertNull(insightDelta(null))
    }
    @Test fun formatsResponseTimeAndWords() {
        assertEquals("보통 1분 안에", insightResponseTime(30))
        assertEquals("보통 2분 안에", insightResponseTime(61))
        assertEquals("보통 1시간 1분 안에", insightResponseTime(3601))
        assertEquals(18, insightWordSize(9, 9))
        assertEquals(15, insightWordSize(4, 9))
        assertEquals(13, insightWordSize(2, 9))
    }
}
