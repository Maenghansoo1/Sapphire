package com.sapphire.hawe.core.push

import com.sapphire.hawe.core.model.NotificationPrefs
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PushModelTest {
    @Test fun kindsAndParsing() {
        assertEquals(PushKind.TOPIC, PushKind.fromValue("topic"))
        assertEquals(PushKind.POKE, PushKind.fromValue("poke"))
        assertEquals(PushKind.COMMENT, PushKind.fromValue("comment"))
        // 그룹원 작품 제출 알림(friend_done)은 없앴어요
        listOf("friend_done", "friendDone", "", "unknown", null).forEach { assertNull(PushKind.fromValue(it)) }
        assertEquals(PushDestination(PushKind.COMMENT, "a", "b"),
            parsePushDestination("comment", "a", "b"))
        assertEquals(PushDestination(PushKind.TOPIC, null, null),
            parsePushDestination("topic", "", " "))
        assertNull(parsePushDestination("unknown", "a", "b"))
        assertNull(parsePushDestination(null, "a", "b"))
    }

    @Test fun targets() {
        val first = PushGroupSnapshot(1L, 101L, false)
        val second = PushGroupSnapshot(2L, 102L, true)
        assertEquals(PushTarget.None, pushTarget(PushDestination(PushKind.TOPIC, null, null), emptyList()))
        listOf(PushKind.TOPIC, PushKind.POKE).forEach {
            assertEquals(PushTarget.Mission(first.id), pushTarget(PushDestination(it, null, null), listOf(first, second)))
        }
        assertEquals(PushTarget.Mission(first.id), pushTarget(PushDestination(PushKind.COMMENT, null, null), listOf(first)))
        assertEquals(PushTarget.Results(second.id, second.lastMissionId!!),
            pushTarget(PushDestination(PushKind.COMMENT, second.id.toString(), null), listOf(first, second)))
        assertEquals(PushTarget.Mission(second.id), pushTarget(PushDestination(PushKind.COMMENT, null, null),
            listOf(second.copy(lastMissionId = null))))
        listOf("mock", UUID.randomUUID().toString()).forEach {
            assertEquals(PushTarget.Mission(first.id),
                pushTarget(PushDestination(PushKind.TOPIC, it, null), listOf(first, second)))
        }
    }

    @Test fun settingsTextsAndChannels() {
        val prefs = NotificationPrefs()
        listOf(PushKind.TOPIC, PushKind.POKE, PushKind.COMMENT).forEach {
            assertTrue(isNotificationEnabled(prefs, it))
            assertFalse(isNotificationEnabled(prefs.copy(all = false), it))
            assertFalse(isNotificationEnabled(prefs, it, false))
            assertTrue(isNotificationEnabled(prefs, it, true))
        }
        assertEquals("HA'WE", defaultPushTexts(PushKind.TOPIC, null).title)
        assertEquals("컴공 동기", defaultPushTexts(PushKind.TOPIC, "컴공 동기").title)
        assertEquals("HA'WE", defaultPushTexts(PushKind.TOPIC, "").title)
        assertEquals("오늘의 주제가 도착했어요", defaultPushTexts(PushKind.TOPIC, null).body)
        assertEquals("누군가 콕 찔렀어요", defaultPushTexts(PushKind.POKE, null).body)
        assertEquals("내 작품에 댓글이 달렸어요", defaultPushTexts(PushKind.COMMENT, null).body)
        assertEquals(PushKind.entries.map { it.value }.toSet(), pushChannelSpecs.map { it.id }.toSet())
        assertEquals(3, pushChannelSpecs.map { it.id }.distinct().size)
        assertEquals(listOf("friend_done"), removedPushChannelIds)
    }
}
