package com.sapphire.hawe.core.net

import kotlinx.serialization.Serializable

@Serializable internal data class NotificationFromDto(val userId: Long, val nickname: String,
    val characterKey: String? = null, val profileImageUrl: String? = null)

@Serializable internal data class NotificationDto(val id: Long, val type: String, val groupId: Long,
    val groupName: String = "", val fromUser: NotificationFromDto? = null, val missionId: Long? = null,
    val submissionId: Long? = null, val body: String? = null, val isRead: Boolean = false, val createdAt: String)

@Serializable internal data class NotificationPageDto(val content: List<NotificationDto>, val page: Int = 0,
    val size: Int = 0, val totalPages: Int = 1, val totalElements: Long = 0)
