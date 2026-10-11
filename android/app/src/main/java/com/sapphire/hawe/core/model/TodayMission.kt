package com.sapphire.hawe.core.model

import java.time.Instant
import java.util.UUID

data class MissionMember(
    val id: UUID, val profile: Profile, val isMe: Boolean = false,
    val isDone: Boolean = false, val pokedToday: Boolean = false,
)

data class TodayMission(
    val id: UUID, val group: GroupDetail, val type: MissionType, val topic: String,
    val number: Int, val opensAt: Instant, val closesAt: Instant,
    val members: List<MissionMember>,
) {
    val doneCount: Int get() = members.count { it.isDone }
    val didSubmit: Boolean get() = members.any { it.isMe && it.isDone }
}
