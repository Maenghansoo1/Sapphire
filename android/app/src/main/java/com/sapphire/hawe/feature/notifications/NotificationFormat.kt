package com.sapphire.hawe.feature.notifications

import com.sapphire.hawe.core.HaweTime
import com.sapphire.hawe.core.model.AppNotification
import com.sapphire.hawe.core.model.NotificationType
import com.sapphire.hawe.designsystem.assets.HIcon
import java.time.Instant

/** 서버 문장(`body`)이 없을 때 앱이 만드는 문장. */
fun notificationText(notification: AppNotification): String {
    notification.body?.let { return it }
    val name = notification.from?.nickname?.let { "${it}님이" } ?: "누군가"
    return when (notification.type) {
        NotificationType.TOPIC -> "오늘의 미션이 열렸어요"
        NotificationType.POKE -> "$name 콕 찔렀어요"
        NotificationType.COMMENT -> "$name 내 작품에 댓글을 남겼어요"
        NotificationType.MENTION -> "$name 댓글에서 나를 불렀어요"
        NotificationType.REACTION -> "$name 내 작품에 반응을 남겼어요"
    }
}

/** 아바타 오른쪽 아래에 붙는 종류 표시. */
fun notificationBadgeIcon(type: NotificationType): HIcon = when (type) {
    NotificationType.TOPIC -> HIcon.BELL
    NotificationType.POKE -> HIcon.POKE_TEMP
    NotificationType.COMMENT, NotificationType.MENTION -> HIcon.COMMENT
    NotificationType.REACTION -> HIcon.HEART_FILL
}

enum class NotificationSection(val title: String) { TODAY("오늘"), EARLIER("이전") }

/** 서울 날짜 기준으로 오늘 받은 알림과 그 전 알림을 나눠요. 순서는 서버가 준 최신순 그대로예요. */
fun notificationSections(items: List<AppNotification>, now: Instant): List<Pair<NotificationSection, List<AppNotification>>> {
    val today = now.atZone(HaweTime.SEOUL).toLocalDate()
    val (todayItems, earlier) = items.partition { it.createdAt.atZone(HaweTime.SEOUL).toLocalDate() == today }
    return listOf(NotificationSection.TODAY to todayItems, NotificationSection.EARLIER to earlier)
        .filter { it.second.isNotEmpty() }
}

sealed interface NotificationTarget {
    data class Mission(val groupId: Long) : NotificationTarget
    data class Results(val groupId: Long, val missionId: Long) : NotificationTarget
    /** 나간 그룹 등 열 수 없는 알림 */
    data object Missing : NotificationTarget
}

/**
 * 누르면 갈 곳 (CONTRACT §8-3 이동 규칙): 미션 도착·콕은 그 그룹 05 화면,
 * 댓글·멘션·반응은 내가 그 미션을 냈으면 결과 화면, 아니면 05 화면.
 */
fun notificationTarget(notification: AppNotification, groupExists: Boolean, canOpenResults: Boolean): NotificationTarget {
    if (!groupExists) return NotificationTarget.Missing
    val missionId = notification.missionId
    val aboutMyWork = notification.type in setOf(NotificationType.COMMENT, NotificationType.MENTION, NotificationType.REACTION)
    return if (aboutMyWork && missionId != null && canOpenResults) NotificationTarget.Results(notification.groupId, missionId)
        else NotificationTarget.Mission(notification.groupId)
}
