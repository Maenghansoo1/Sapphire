package com.sapphire.hawe.core.net

import kotlinx.serialization.Serializable

@Serializable internal data class CalendarDto(val month: String, val days: List<CalendarDayDto>)
@Serializable internal data class CalendarDayDto(val date: String, val missions: List<CalendarMissionDto>)
@Serializable internal data class CalendarMissionDto(val missionId: Long, val media: String? = null,
    val content: String, val submittedCount: Int, val memberCount: Int, val stamp: String? = null)
@Serializable internal data class MyStatsDto(val completedMissions: Int, val streakDays: Int,
    val groupCount: Int, val byMedia: Map<String, Int> = emptyMap())
