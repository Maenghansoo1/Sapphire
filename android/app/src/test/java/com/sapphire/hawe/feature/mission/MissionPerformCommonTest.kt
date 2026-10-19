package com.sapphire.hawe.feature.mission

import com.sapphire.hawe.core.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class MissionPerformCommonTest {
    private fun mission(done: Boolean = false, type: MissionType = MissionType.WRITE,
        template: String = MissionTemplates.TEXT_ACROSTIC) = TodayMission(
        100L, GroupDetail.mock, type, "주제", 1, Instant.EPOCH, Instant.EPOCH,
        listOf(MissionMember(1L, Profile.mockMine, isMe = true, isDone = done)), template = template)

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

    @Test fun newTemplatesOpenAndUnknownTemplatesAskForUpdate() {
        listOf(MissionTemplates.TEXT_ONE_WORD, MissionTemplates.TEXT_REVIEW, MissionTemplates.TEXT_BALANCE).forEach {
            assertEquals(PerformEntryDecision.Allow(MissionType.WRITE), performEntryDecision(mission(template = it), null))
        }
        // 서버가 나중에 켤 2차 틀(매체는 알아도 화면이 없음)·빈 틀은 열지 않아요
        assertEquals(PerformEntryDecision.Unsupported, performEntryDecision(mission(template = "TEXT_RELAY"), null))
        // 없앤 슬라이더 틀이 예전 서버에서 와도 열지 않아요
        assertEquals(PerformEntryDecision.Unsupported, performEntryDecision(mission(template = "SLIDER_POSITION"), null))
        assertEquals(PerformEntryDecision.Unsupported, performEntryDecision(mission(template = ""), null))
        // 이미 냈으면 틀과 상관없이 "이미 냈어요"
        assertEquals(PerformEntryDecision.AlreadySubmitted, performEntryDecision(mission(true, template = "TEXT_RELAY"), null))
    }
}
