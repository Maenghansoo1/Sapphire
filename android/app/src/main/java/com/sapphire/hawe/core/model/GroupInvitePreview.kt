package com.sapphire.hawe.core.model

import com.sapphire.hawe.designsystem.assets.Relationship

data class GroupInvitePreview(val name: String, val relationship: Relationship, val members: List<AvatarContent>,
    val memberCount: Int = members.size, val maxMembers: Int = relationship.maxMembers) {
    val isFull: Boolean get() = memberCount >= maxMembers
}
