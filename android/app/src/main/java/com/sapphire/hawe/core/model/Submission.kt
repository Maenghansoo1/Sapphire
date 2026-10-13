package com.sapphire.hawe.core.model

import java.net.URI
import java.time.Instant

data class Submission(
    val id: Long, val author: Profile, val isMine: Boolean, val type: MissionType,
    val createdAt: Instant, val mediaURL: URI? = null, val text: String? = null,
    val isLate: Boolean = false, val authorID: Long? = null,
    val reactions: List<Reaction> = emptyList(), val commentCount: Int = 0,
)

/** 서버 스티커 종류(`stickerType`). 디자인이 나오기 전까지 이모지로 임시 표시해요. */
enum class Sticker(val key: String, val emoji: String, val label: String) {
    HEART("heart", "❤️", "하트"), LAUGH("laugh", "😂", "웃김"), WOW("wow", "😮", "놀람"),
    SAD("sad", "😢", "슬픔"), FIRE("fire", "🔥", "최고");
    companion object { fun fromKey(key: String): Sticker? = entries.firstOrNull { it.key == key } }
}

data class Reaction(val sticker: Sticker, val count: Int, val reactedByMe: Boolean, val userIds: List<Long>)

data class SubmissionComment(
    val id: Long, val author: Profile, val authorID: Long, val content: String, val imageURL: URI?,
    val mentionedUserIds: List<Long>, val createdAt: Instant, val isMine: Boolean,
)

data class MissionResult(
    val id: Long, val groupName: String, val type: MissionType, val topic: String,
    val date: Instant, val memberCount: Int, val submissions: List<Submission>, val pending: List<Profile>,
)
