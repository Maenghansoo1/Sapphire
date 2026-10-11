package com.sapphire.hawe.core

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

object HaweTime {
    val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

    fun window(topicTime: LocalTime, now: Instant): Pair<Instant, Instant> {
        val today = now.atZone(SEOUL).toLocalDate()
        val todayOpen = today.atTime(topicTime).atZone(SEOUL).toInstant()
        val opens = if (now >= todayOpen) todayOpen else today.minusDays(1).atTime(topicTime).atZone(SEOUL).toInstant()
        val closes = opens.atZone(SEOUL).plusDays(1).toInstant()
        return opens to closes
    }
}
