package com.sapphire.hawe.core.model

import com.sapphire.hawe.designsystem.assets.Relationship

data class GroupInvitePreview(val name: String, val relationship: Relationship, val members: List<AvatarContent>) {
    val isFull: Boolean get() = members.size >= relationship.maxMembers
}
