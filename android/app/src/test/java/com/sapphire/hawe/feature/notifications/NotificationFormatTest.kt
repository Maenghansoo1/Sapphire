package com.sapphire.hawe.feature.notifications

import com.sapphire.hawe.core.model.AppNotification
import com.sapphire.hawe.core.model.AvatarContent
import com.sapphire.hawe.core.model.NotificationType
import com.sapphire.hawe.core.model.Profile
import com.sapphire.hawe.designsystem.assets.ProfileCharacter
import com.sapphire.hawe.designsystem.assets.HIcon
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class NotificationFormatTest {
    private val now = Instant.parse("2026-10-19T06:00:00Z") // 서울 15:00
    private fun item(type: NotificationType, from: String? = "서연", body: String? = null,
        missionId: Long? = 10, at: Instant = now) = AppNotification(1, type, 3, "컴공 동기",
        from?.let { Profile(AvatarContent.Character(ProfileCharacter.MULBANGUL), it) }, missionId, null, body, false, at)

    @Test fun fallbackTextPerType() {
        assertEquals(HIcon.BELL, notificationBadgeIcon(NotificationType.POKE))
        assertEquals("오늘의 미션이 열렸어요", notificationText(item(NotificationType.TOPIC, from = null)))
        assertEquals("서연님이 콕 찔렀어요", notificationText(item(NotificationType.POKE)))
        assertEquals("서연님이 내 작품에 댓글을 남겼어요", notificationText(item(NotificationType.COMMENT)))
        assertEquals("서연님이 댓글에서 나를 불렀어요", notificationText(item(NotificationType.MENTION)))
        assertEquals("서연님이 내 작품에 반응을 남겼어요", notificationText(item(NotificationType.REACTION)))
        assertEquals("서버 문장", notificationText(item(NotificationType.POKE, body = "서버 문장")))
    }

    @Test fun targetFollowsContractRules() {
        assertEquals(NotificationTarget.Missing, notificationTarget(item(NotificationType.POKE), false, true))
        assertEquals(NotificationTarget.Mission(3), notificationTarget(item(NotificationType.POKE), true, true))
        assertEquals(NotificationTarget.Mission(3), notificationTarget(item(NotificationType.TOPIC, from = null), true, true))
        assertEquals(NotificationTarget.Results(3, 10), notificationTarget(item(NotificationType.COMMENT), true, true))
        assertEquals(NotificationTarget.Results(3, 10), notificationTarget(item(NotificationType.REACTION), true, true))
        // 내가 아직 안 냈거나 미션을 모르면 05 화면으로
        assertEquals(NotificationTarget.Mission(3), notificationTarget(item(NotificationType.MENTION), true, false))
        assertEquals(NotificationTarget.Mission(3), notificationTarget(item(NotificationType.COMMENT, missionId = null), true, true))
    }

    @Test fun splitsTodayAndEarlierBySeoulDate() {
        val today = item(NotificationType.POKE, at = Instant.parse("2026-10-18T15:30:00Z")) // 서울 10/19 00:30
        val yesterday = item(NotificationType.POKE, at = Instant.parse("2026-10-18T14:30:00Z")) // 서울 10/18 23:30
        val sections = notificationSections(listOf(today, yesterday), now)
        assertEquals(listOf(NotificationSection.TODAY, NotificationSection.EARLIER), sections.map { it.first })
        assertEquals(listOf(listOf(today), listOf(yesterday)), sections.map { it.second })
        assertEquals(listOf(NotificationSection.EARLIER), notificationSections(listOf(yesterday), now).map { it.first })
    }
}
