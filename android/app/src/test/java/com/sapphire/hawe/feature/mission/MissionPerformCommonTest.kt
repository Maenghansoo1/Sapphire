package com.sapphire.hawe.feature.mission

import com.sapphire.hawe.core.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

class MissionPerformCommonTest {
    private fun mission(done: Boolean = false, type: MissionType = MissionType.WRITE) = TodayMission(
        UUID.randomUUID(), GroupDetail.mock, type, "주제", 1, Instant.EPOCH, Instant.EPOCH,
        listOf(MissionMember(UUID.randomUUID(), Profile.mockMine, isMe = true, isDone = done)))

    @Test fun onlyWriteAndPhotoAvailable() {
        assertTrue(isPerformScreenAvailable(MissionType.WRITE))
        assertTrue(isPerformScreenAvailable(MissionType.PHOTO))
        assertFalse(isPerformScreenAvailable(MissionType.DRAW))
        assertFalse(isPerformScreenAvailable(MissionType.VOICE))
    }

    @Test fun entryDecisions() {
        assertEquals(PerformEntryDecision.Missing, performEntryDecision(null, null))
        assertEquals(PerformEntryDecision.AlreadySubmitted, performEntryDecision(mission(true), null))
        assertEquals(PerformEntryDecision.Allow(MissionType.WRITE), performEntryDecision(mission(), null))
        assertEquals(PerformEntryDecision.Allow(MissionType.PHOTO), performEntryDecision(mission(), MissionType.PHOTO))
        assertEquals(PerformEntryDecision.Unsupported, performEntryDecision(mission(), MissionType.DRAW))
        assertEquals(PerformEntryDecision.Unsupported, performEntryDecision(mission(), MissionType.VOICE))
    }
}
