package com.sapphire.hawe.core.model

import java.time.Instant

/** 앱 안 알림 종류. `wire` 는 서버 `type` 값(CONTRACT_DECISIONS §8-3, `reaction` 은 추가 요청). */
enum class NotificationType(val wire: String) {
    TOPIC("topic"), POKE("poke"), COMMENT("comment"), MENTION("mention"),
    REACTION("reaction");
    companion object { fun fromWire(value: String): NotificationType? = entries.firstOrNull { it.wire == value } }
}

/** 알림 목록 한 줄. [from] 은 그 그룹의 표시 프로필이고, 미션 도착(`topic`)처럼 보낸 사람이 없으면 null. */
data class AppNotification(
    val id: Long,
    val type: NotificationType,
    val groupId: Long,
    val groupName: String,
    val from: Profile?,
    val missionId: Long?,
    val submissionId: Long?,
    val body: String?,
    val isRead: Boolean,
    val createdAt: Instant,
)
