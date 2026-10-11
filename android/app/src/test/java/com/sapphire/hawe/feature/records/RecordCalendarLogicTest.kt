package com.sapphire.hawe.feature.records

import com.sapphire.hawe.core.model.DayRecord
import com.sapphire.hawe.core.model.MissionType
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class RecordCalendarLogicTest {
    private fun record(at: String, type: MissionType = MissionType.DRAW) =
        DayRecord(Instant.parse(at), type, "주제", 3, 5, true, UUID.randomUUID())

    @Test fun weeksStartOnSundayAndPadWithNulls() {
        // 2026-10-01 은 목요일: 앞에 빈칸 4개
        val october = monthWeeks(YearMonth.of(2026, 10))
        assertEquals(5, october.size)
        assertTrue(october.all { it.size == 7 })
        assertEquals(listOf(null, null, null, null), october[0].take(4))
        assertEquals(LocalDate.of(2026, 10, 1), october[0][4])
        assertEquals(LocalDate.of(2026, 10, 31), october[4][6])
        // 2026-02 는 일요일 시작 28일: 빈칸 없이 4주
        val february = monthWeeks(YearMonth.of(2026, 2))
        assertEquals(4, february.size)
        assertEquals(LocalDate.of(2026, 2, 1), february[0][0])
        assertEquals(28, february.flatten().count { it != null })
        // 뒤쪽에도 빈칸이 생기는 달
        val june = monthWeeks(YearMonth.of(2026, 6))
        assertEquals(30, june.flatten().count { it != null })
        assertNull(june.last().last())
    }

    @Test fun monthNavigationBounds() {
        val first = YearMonth.of(2026, 6)
        val last = YearMonth.of(2026, 10)
        assertFalse(canGoPrevious(first, first))
        assertTrue(canGoPrevious(YearMonth.of(2026, 7), first))
        assertFalse(canGoNext(last, last))
        assertTrue(canGoNext(YearMonth.of(2026, 9), last))
        assertEquals("2026년 10월", monthTitle(last))
        // 서울 기준: UTC 5월 31일 15시 = 서울 6월 1일
        assertEquals(first, firstMonth(Instant.parse("2026-05-31T15:00:00Z")))
        assertEquals(YearMonth.of(2026, 5), firstMonth(Instant.parse("2026-05-31T14:59:00Z")))
    }

    @Test fun recordsUseSeoulDatesAndKeepFirstOfDay() {
        val byDate = recordsByDate(listOf(
            record("2026-10-10T00:00:00Z"), record("2026-10-11T15:00:00Z", MissionType.WRITE),
            record("2026-10-11T16:00:00Z", MissionType.VOICE)))
        assertEquals(setOf(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 12)), byDate.keys)
        assertEquals(MissionType.WRITE, byDate[LocalDate.of(2026, 10, 12)]!!.type)
        assertEquals(LocalDate.of(2026, 10, 12), initialSelectedDate(byDate))
        assertNull(initialSelectedDate(emptyMap()))
    }

    @Test fun cellDescriptionMentionsTodayAndType() {
        val date = LocalDate.of(2026, 10, 12)
        assertEquals("10월 12일", cellDescription(date, null, false))
        assertEquals("10월 12일, 오늘", cellDescription(date, null, true))
        assertEquals("10월 12일, 오늘, 글 기록",
            cellDescription(date, record("2026-10-12T00:00:00Z", MissionType.WRITE), true))
    }
}
