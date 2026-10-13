package com.sapphire.hawe.feature.results

import com.sapphire.hawe.core.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

class ResultsFormatTest {
    private val now = Instant.parse("2026-10-12T00:00:00Z")

    @Test fun seoulTimeIgnoresDeviceSettings() {
        val zone = TimeZone.getDefault()
        val locale = Locale.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            Locale.setDefault(Locale.US)
            assertEquals("오전 9:12", koreanTimeText(Instant.parse("2026-10-12T00:12:00Z")))
            assertEquals("오전 12:05", koreanTimeText(Instant.parse("2026-10-11T15:05:00Z")))
            assertEquals("오후 12:00", koreanTimeText(Instant.parse("2026-10-12T03:00:00Z")))
            assertEquals("오후 11:59", koreanTimeText(Instant.parse("2026-10-12T14:59:00Z")))
            assertEquals("10월 12일", koreanMonthDayText(Instant.parse("2026-10-11T15:00:00Z")))
        } finally {
            TimeZone.setDefault(zone)
            Locale.setDefault(locale)
        }
    }

    @Test fun relativeTime() {
        assertEquals("방금", koreanRelativeText(now.minusSeconds(30), now))
        assertEquals("방금", koreanRelativeText(now.minusSeconds(59), now))
        assertEquals("1분 전", koreanRelativeText(now.minusSeconds(60), now))
        assertEquals("59분 전", koreanRelativeText(now.minusSeconds(3540), now))
        assertEquals("2시간 전", koreanRelativeText(now.minusSeconds(7200), now))
        assertEquals("1일 전", koreanRelativeText(now.minusSeconds(90000), now))
        assertEquals("1주 전", koreanRelativeText(now.minusSeconds(8 * 86400), now))
        assertEquals("방금", koreanRelativeText(now.plusSeconds(10), now))
    }

    @Test fun feedAndCommentCopy() {
        assertEquals("3 / 5명 완료", progressText(3, 5))
        assertEquals("5명 모두 완료", progressText(5, 5))
        assertEquals("1명 모두 완료", progressText(1, 1))
        assertEquals("0 / 4명 완료", progressText(0, 4))
        assertEquals("사진을 눌러서 자세히 보기", hintText(MissionType.PHOTO))
        assertEquals("그림을 눌러서 자세히 보기", hintText(MissionType.DRAW))
        assertEquals("글을 눌러서 자세히 보기", hintText(MissionType.WRITE))
        assertNull(likersText(emptyList(), 0))
        assertEquals("지우님이 좋아해요", likersText(listOf("지우"), 1))
        assertEquals("지우, 서연님이 좋아해요", likersText(listOf("지우", "서연"), 2))
        assertEquals("지우, 서연님 외 3명이 좋아해요", likersText(listOf("지우", "서연"), 5))
        assertEquals("지우, 서연님이 좋아해요", likersText(listOf("지우", "서연"), 1))
        assertFalse(commentSendEnabled(""))
        assertFalse(commentSendEnabled("  "))
        assertFalse(commentSendEnabled("\n"))
        assertTrue(commentSendEnabled(" 안녕 "))
        assertEquals("민준님에게 댓글 남기기", commentPlaceholder("민준"))
    }

    @Test fun localImage() {
        val submission = Submission(1L, Profile.mockMine, true, MissionType.PHOTO, now)
        assertNull(localImageFile(submission))
        val file = File.createTempFile("hawe-result-", ".png")
        try {
            assertEquals(file, localImageFile(submission.copy(mediaURL = file.toURI())))
        } finally { file.delete() }
        assertNull(localImageFile(submission.copy(mediaURL = file.toURI())))
        assertNull(localImageFile(submission.copy(mediaURL = java.net.URI("https://example.com/a.png"))))
    }
}
