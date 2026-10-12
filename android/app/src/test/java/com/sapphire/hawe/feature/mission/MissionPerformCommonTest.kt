package com.sapphire.hawe.feature.mission

import com.sapphire.hawe.core.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class MissionPerformCommonTest {
    private fun mission(done: Boolean = false, type: MissionType = MissionType.WRITE) = TodayMission(
        100L, GroupDetail.mock, type, "주제", 1, Instant.EPOCH, Instant.EPOCH,
        listOf(MissionMember(1L, Profile.mockMine, isMe = true, isDone = done)))

    @Test fun allTypesAvailable() {
        MissionType.entries.forEach { assertTrue(isPerformScreenAvailable(it)) }
    }

    @Test fun entryDecisions() {
        assertEquals(PerformEntryDecision.Missing, performEntryDecision(null, null))
        assertEquals(PerformEntryDecision.AlreadySubmitted, performEntryDecision(mission(true), null))
        assertEquals(PerformEntryDecision.Allow(MissionType.WRITE), performEntryDecision(mission(), null))
        assertEquals(PerformEntryDecision.Unsupported, performEntryDecision(mission(), MissionType.PHOTO))
        assertEquals(PerformEntryDecision.Unsupported, performEntryDecision(mission().copy(type = null), null))
    }
}
