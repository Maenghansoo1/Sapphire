package com.sapphire.hawe.core.net

import com.sapphire.hawe.core.HaweTime
import com.sapphire.hawe.core.model.ActivityStats
import com.sapphire.hawe.core.model.DayRecord
import com.sapphire.hawe.core.model.Stamp
import java.time.LocalDate
import java.time.YearMonth

internal fun stampOf(value: String?): Stamp? =
    value?.let { runCatching { Stamp.valueOf(it) }.getOrNull() }

class CalendarRepository(private val api: ApiClient) {
    /** 그 달에 미션이 있었던 날의 기록. 앱이 모르는 유형(media)의 미션은 빼요. */
    suspend fun month(groupId: Long, month: YearMonth): List<DayRecord> {
        val response: CalendarDto = api.get("/api/groups/$groupId/calendar?month=$month")
        return response.days.flatMap { day ->
            val date = LocalDate.parse(day.date).atStartOfDay(HaweTime.SEOUL).toInstant()
            day.missions.mapNotNull { mission ->
                val type = mission.media?.let(::mediaToType) ?: return@mapNotNull null
                val stamp = stampOf(mission.stamp)
                DayRecord(date, type, mission.content, mission.submittedCount, mission.memberCount,
                    didSubmit = stamp == Stamp.GOOD || stamp == Stamp.LATE, missionID = mission.missionId,
                    stamp = stamp)
            }
        }
    }

    suspend fun stats(): ActivityStats {
        val response: MyStatsDto = api.get("/api/me/stats")
        val byType = response.byMedia.mapNotNull { (media, count) -> mediaToType(media)?.let { it to count } }
            .toMap()
        return ActivityStats(response.completedMissions, response.streakDays, response.groupCount, byType)
    }
}
