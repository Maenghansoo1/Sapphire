package com.sapphire.hawe.core.net

import com.sapphire.hawe.core.model.AppNotification
import com.sapphire.hawe.core.model.NotificationType
import com.sapphire.hawe.core.model.Profile
import java.time.OffsetDateTime

/** 앱 안 알림 목록(`GET /api/notifications`, CONTRACT_DECISIONS §8-3). 앱이 모르는 종류는 빼요. */
class NotificationRepository(private val api: ApiClient) {
    suspend fun list(page: Int = 0, size: Int = 50): List<AppNotification> {
        val dto: NotificationPageDto = api.get("/api/notifications?page=$page&size=$size")
        return dto.content.mapNotNull { it.toModel() }
    }

    suspend fun markRead(id: Long) {
        api.execute("PATCH", "/api/notifications/$id/read", "{}", true)
    }

    suspend fun markAllRead() {
        api.execute("PATCH", "/api/notifications/read-all", "{}", true)
    }

    private fun NotificationDto.toModel(): AppNotification? {
        val kind = NotificationType.fromWire(type) ?: return null
        val from = fromUser?.let { Profile(api.avatarOf(it.characterKey, it.profileImageUrl), it.nickname) }
        return AppNotification(id, kind, groupId, groupName, from, missionId, submissionId,
            body?.takeIf(String::isNotBlank), isRead, OffsetDateTime.parse(createdAt).toInstant())
    }
}
