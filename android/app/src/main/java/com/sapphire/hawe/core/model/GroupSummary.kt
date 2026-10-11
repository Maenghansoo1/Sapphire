package com.sapphire.hawe.core.model

import com.sapphire.hawe.core.HaweTime
import com.sapphire.hawe.designsystem.assets.Relationship
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

data class GroupSummary(
    val id: UUID, val name: String, val relationship: Relationship,
    val members: List<AvatarContent>, val startDate: LocalDate?,
    val missionType: MissionType, val topic: String,
    val submittedCount: Int, val didSubmit: Boolean,
) {
    fun daysTogether(on: Instant = Instant.now()): Int? = startDate?.let {
        ChronoUnit.DAYS.between(it, on.atZone(HaweTime.SEOUL).toLocalDate()).toInt() + 1
    }
}
