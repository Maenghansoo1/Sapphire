package com.sapphire.hawe.feature.groups

import com.sapphire.hawe.core.model.AvatarContent
import com.sapphire.hawe.core.model.GroupMember
import com.sapphire.hawe.core.model.Profile

data class GroupProfileState(
    val defaultProfile: Profile, val useDefault: Boolean,
    val avatar: AvatarContent, val nickname: String, val originalProfile: Profile?,
) {
    val preview: Profile get() = if (useDefault) defaultProfile else Profile(avatar, nickname.ifEmpty { " " })
    val canSave: Boolean get() = useDefault || nickname.trim().length in 2..10
    val profileToSave: Profile? get() = if (useDefault) null else Profile(avatar, nickname.trim())
    fun withNickname(input: String): GroupProfileState = copy(nickname = input.take(10))

    companion object {
        fun from(member: GroupMember, myDefault: Profile): GroupProfileState = GroupProfileState(
            myDefault, member.groupProfile == null, member.displayed.avatar, member.displayed.nickname,
            member.groupProfile)
    }
}

fun needsPhotoReselect(useDefault: Boolean, scheme: String?, nickname: String, originalNickname: String?): Boolean =
    !useDefault && scheme in listOf("http", "https") &&
        (originalNickname == null || nickname.trim() != originalNickname)
