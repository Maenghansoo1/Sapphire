package com.sapphire.hawe.core.model

import java.time.YearMonth

data class ActivityIndex(val score: Int, val delta: Int?, val participationRate: Double,
    val onTimeRate: Double, val interactionRate: Double)

data class FastestMember(val userId: Long, val profile: Profile, val firstCount: Int,
    val averageResponseSeconds: Long, val fastestSingleSeconds: Long)

data class TopWord(val word: String, val count: Int, val memberCount: Int)

data class MostReacted(val submissionId: Long, val missionId: Long, val missionContent: String,
    val authorNickname: String, val reactionCount: Int, val commentCount: Int, val topSticker: Sticker?)

data class GroupInsight(val month: YearMonth, val stale: Boolean, val empty: Boolean,
    val activityIndex: ActivityIndex?, val fastest: FastestMember?, val topWords: List<TopWord>,
    val mostReacted: MostReacted?, val highlights: List<String>)
