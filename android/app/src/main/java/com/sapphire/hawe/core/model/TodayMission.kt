package com.sapphire.hawe.core.model

import java.time.Instant

data class MissionMember(
    val id: Long, val profile: Profile, val isMe: Boolean = false,
    val isDone: Boolean = false, val poked: Set<PokeKind> = emptySet(),
)

data class TodayMission(
    val id: Long, val group: GroupDetail, val type: MissionType?, val topic: String,
    val number: Int, val opensAt: Instant, val closesAt: Instant,
    val members: List<MissionMember>,
    val deadlineAt: Instant = closesAt, val template: String = "", val spec: MissionSpec = MissionSpec(),
) {
    val doneCount: Int get() = members.count { it.isDone }
    val didSubmit: Boolean get() = members.any { it.isMe && it.isDone }
}
