package com.sapphire.hawe.feature.groups

import com.sapphire.hawe.core.model.*
import com.sapphire.hawe.designsystem.assets.Relationship
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import java.util.UUID

class GroupFormatTest {
    @Test fun homeMissionCopyChangesOnlyForMultipleMissions() {
        val single = GroupSummary(1, "그룹", Relationship.FRIEND, emptyList(), null,
            MissionType.WRITE, "주제", 1, false, 1, 0)
        assertEquals("오늘의 미션 · ${MissionType.WRITE.displayName}", homeMissionLabel(single))
        assertNull(homeSubmissionProgress(single))
        val multiple = single.copy(missionCount = 3, mySubmittedCount = 1)
        assertEquals("오늘의 미션 · ${MissionType.WRITE.displayName} · 외 2개", homeMissionLabel(multiple))
        assertEquals("1 / 3 제출", homeSubmissionProgress(multiple))
    }
    @Test fun topicTimeUsesKoreanTwelveHourClock() {
        assertEquals("오전 9:00", topicTimeText(LocalTime.of(9, 0)))
        assertEquals("오전 12:00", topicTimeText(LocalTime.MIDNIGHT))
        assertEquals("오후 12:00", topicTimeText(LocalTime.NOON))
        assertEquals("오후 1:05", topicTimeText(LocalTime.of(13, 5)))
    }

    @Test fun datesUseSeoulAndKoreanLocale() {
        assertEquals("2026. 6. 2.", dotDate(LocalDate.of(2026, 6, 2)))
        assertEquals("2026. 6. 2.", dotDate(Instant.parse("2026-06-01T15:01:00Z")))
        assertEquals("2026. 6. 1.", dotDate(Instant.parse("2026-06-01T14:59:00Z")))
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("10월 12일 월요일", homeDateText(Instant.parse("2026-10-11T15:01:00Z")))
        } finally { Locale.setDefault(original) }
    }

    @Test fun badgesAndLeaveMessage() {
        val mine = GroupMember(1L, Profile.mockMine, isMe = true)
        val owner = mine.copy(isMe = false, isOwner = true)
        assertEquals("나", memberBadge(mine))
        assertEquals("방장", memberBadge(owner))
        assertEquals("나 · 방장", memberBadge(mine.copy(isOwner = true)))
        assertNull(memberBadge(mine.copy(isMe = false)))
        val group = GroupDetail.mock.copy(name = "우리", members = listOf(mine.copy(isOwner = true)))
        assertTrue(leaveMessage(group).contains("그룹이 삭제돼요"))
        assertFalse(leaveMessage(group.copy(members = listOf(mine.copy(isOwner = true), owner))).contains("가장 먼저 들어온"))
        assertFalse(leaveMessage(group.copy(members = listOf(mine, owner))).contains("방장은"))
        assertEquals(LeaveFlow.OWNER_ALONE, leaveFlowFor(group))
        assertEquals(LeaveFlow.OWNER_WITH_MEMBERS, leaveFlowFor(group.copy(members = listOf(mine.copy(isOwner = true), owner))))
        assertEquals(LeaveFlow.MEMBER, leaveFlowFor(group.copy(members = listOf(mine, owner))))
    }

    @Test fun cardSubtitleIncludesLoverDayCount() {
        val avatars = List(4) { Profile.mockMine.avatar }
        val base = GroupSummary(1L, "친구들", Relationship.FRIEND, avatars, null,
            MissionType.DRAW, "주제", 0, false)
        assertEquals("친구 · 4명", groupCardSubtitle(base))
        val lover = base.copy(relationship = Relationship.LOVER, members = avatars.take(2),
            startDate = LocalDate.of(2026, 6, 2))
        assertEquals("연인 · 2명 · 함께한 지 128일",
            groupCardSubtitle(lover, Instant.parse("2026-10-06T15:00:00Z")))
    }
}
