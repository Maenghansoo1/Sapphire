package com.sapphire.hawe.core.model

import java.time.Instant
import java.util.UUID

data class DayRecord(
    val date: Instant, val type: MissionType, val topic: String,
    val doneCount: Int, val memberCount: Int, val didSubmit: Boolean, val missionID: UUID? = null,
)
