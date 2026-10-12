package com.sapphire.hawe.feature.mission

import com.sapphire.hawe.core.model.MissionMember
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.Profile
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

class MissionFormatTest {
    private val now = Instant.parse("2026-10-12T00:00:00Z")

    @Test fun countdownClampsAndPadsHours() {
        assertEquals("00:00:00", countdownText(now, now))
        assertEquals("00:00:00", countdownText(now.minusSeconds(1), now))
        assertEquals("00:00:59", countdownText(now.plusSeconds(59), now))
        assertEquals("01:01:01", countdownText(now.plusSeconds(3661), now))
        assertEquals("24:00:00", countdownText(now.plusSeconds(86400), now))
        assertEquals("100:00:00", countdownText(now.plusSeconds(360000), now))
        assertEquals("00:00:01", countdownText(now.plusMillis(1900), now))
    }

    @Test fun dateUsesSeoulRegardlessOfDeviceSettings() {
        val originalZone = TimeZone.getDefault()
        val originalLocale = Locale.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            Locale.setDefault(Locale.US)
            assertEquals("10월 11일", missionDateText(Instant.parse("2026-10-11T14:59:59Z")))
            assertEquals("10월 12일", missionDateText(Instant.parse("2026-10-11T15:00:00Z")))
        } finally {
            TimeZone.setDefault(originalZone)
            Locale.setDefault(originalLocale)
        }
    }

    @Test fun memberAndNoticeCopy() {
        val mine = MissionMember(1L, Profile.mockMine, isMe = true)
        assertEquals("하늘 (나)", memberDisplayName(mine))
        assertEquals("하늘", memberDisplayName(mine.copy(isMe = false)))
        assertEquals("내 미션을 올리면 친구들의 그림이 열려요.", lockNoticeText(MissionType.DRAW))
        assertEquals("내 미션을 올리면 친구들의 사진이 열려요.", lockNoticeText(MissionType.PHOTO))
        assertEquals("내 미션을 올리면 친구들의 글이 열려요.", lockNoticeText(MissionType.WRITE))
        assertEquals("내 미션을 올리면 친구들의 음성이 열려요.", lockNoticeText(MissionType.VOICE))
        assertEquals("민준님을 콕 찔렀어요", pokeToastText("민준"))
    }

    @Test fun deadlineShowsNormalLateAndClosedStates() {
        val deadline = Instant.parse("2026-10-12T14:00:00Z")
        val closes = Instant.parse("2026-10-13T00:00:00Z")
        assertEquals("마감 오후 11:00", deadlineText(deadline, closes, now))
        assertEquals("마감이 지났어요 · 지금 올리면 지각이에요", deadlineText(deadline, closes, deadline))
        assertNull(deadlineText(deadline, closes, closes))
        val nextDay = Instant.parse("2026-10-13T14:00:00Z")
        assertEquals("마감 내일 오후 11:00", deadlineText(nextDay, nextDay.plusSeconds(3600), now))
    }
}
