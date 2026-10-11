package com.sapphire.hawe.core.model

import com.sapphire.hawe.designsystem.assets.ProfileCharacter
import com.sapphire.hawe.designsystem.assets.Relationship
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class Profile(val avatar: AvatarContent, val nickname: String) {
    companion object { val mockMine get() = Profile(AvatarContent.Character(ProfileCharacter.MONGSIL), "하늘") }
}

sealed interface AvatarContent {
    data class Character(val character: ProfileCharacter) : AvatarContent
    data class Photo(val uri: android.net.Uri) : AvatarContent
    val isPhoto: Boolean get() = this is Photo
}

data class GroupMember(
    val id: UUID,
    val defaultProfile: Profile,
    val groupProfile: Profile? = null,
    val isMe: Boolean = false,
    val isOwner: Boolean = false,
) { val displayed: Profile get() = groupProfile ?: defaultProfile }

data class GroupDetail(
    val id: UUID,
    val name: String,
    val relationship: Relationship,
    val createdAt: Instant,
    val missionCount: Int,
    val inviteCode: String,
    val topicTime: LocalTime = LocalTime.of(9, 0),
    val notify: Boolean = true,
    val members: List<GroupMember>,
    val startDate: LocalDate? = null,
) {
    val me: GroupMember? get() = members.firstOrNull { it.isMe }
    val amIOwner: Boolean get() = me?.isOwner == true

    companion object {
        val mock get() = GroupDetail(
            UUID.randomUUID(), "컴공 동기", Relationship.FRIEND,
            LocalDate.of(2026, 6, 2).atStartOfDay(com.sapphire.hawe.core.HaweTime.SEOUL).toInstant(),
            142, "K7Q2PX", members = listOf(
                GroupMember(UUID.randomUUID(), Profile.mockMine, Profile(AvatarContent.Character(ProfileCharacter.GOMDOL), "과대표 하늘"), true, true),
                GroupMember(UUID.randomUUID(), Profile(AvatarContent.Character(ProfileCharacter.GAMJA), "민준")),
                GroupMember(UUID.randomUUID(), Profile(AvatarContent.Character(ProfileCharacter.KKOTNIM), "지우")),
                GroupMember(UUID.randomUUID(), Profile(AvatarContent.Character(ProfileCharacter.MULBANGUL), "서연")),
                GroupMember(UUID.randomUUID(), Profile(AvatarContent.Character(ProfileCharacter.PILLEUM), "현우")),
            )
        )
    }
}
