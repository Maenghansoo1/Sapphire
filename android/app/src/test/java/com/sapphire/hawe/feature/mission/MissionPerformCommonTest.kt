package com.sapphire.hawe.feature.mission

import com.sapphire.hawe.core.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

class MissionPerformCommonTest {
    private fun mission(done: Boolean = false, type: MissionType = MissionType.WRITE) = TodayMission(
        UUID.randomUUID(), GroupDetail.mock, type, "주제", 1, Instant.EPOCH, Instant.EPOCH,
        listOf(MissionMember(1L, Profile.mockMine, isMe = true, isDone = done)))

    @Test fun allTypesAvailable() {
        MissionType.entries.forEach { assertTrue(isPerformScreenAvailable(it)) }
    }

    @Test fun entryDecisions() {
        assertEquals(PerformEntryDecision.Missing, performEntryDecision(null, null))
        assertEquals(PerformEntryDecision.AlreadySubmitted, performEntryDecision(mission(true), null))
        assertEquals(PerformEntryDecision.Allow(MissionType.WRITE), performEntryDecision(mission(), null))
        assertEquals(PerformEntryDecision.Allow(MissionType.PHOTO), performEntryDecision(mission(), MissionType.PHOTO))
        assertEquals(PerformEntryDecision.Allow(MissionType.DRAW), performEntryDecision(mission(), MissionType.DRAW))
        assertEquals(PerformEntryDecision.Allow(MissionType.VOICE), performEntryDecision(mission(), MissionType.VOICE))
    }
}
