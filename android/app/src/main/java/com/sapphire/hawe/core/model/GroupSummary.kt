package com.sapphire.hawe.core.model

import com.sapphire.hawe.core.HaweTime
import com.sapphire.hawe.core.dev.AppClock
import com.sapphire.hawe.designsystem.assets.Relationship
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class GroupSummary(
    val id: Long, val name: String, val relationship: Relationship,
    val members: List<AvatarContent>, val startDate: LocalDate?,
    val missionType: MissionType?, val topic: String?,
    val submittedCount: Int, val didSubmit: Boolean,
    val missionCount: Int = 0, val mySubmittedCount: Int = 0,
) {
    fun daysTogether(on: Instant = AppClock.now()): Int? = startDate?.let {
        ChronoUnit.DAYS.between(it, on.atZone(HaweTime.SEOUL).toLocalDate()).toInt() + 1
    }
}
