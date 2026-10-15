package com.sapphire.hawe.core.net

import kotlinx.serialization.Serializable

@Serializable internal data class InsightDto(val groupId: Long, val month: String, val status: String,
    val retryAfterSeconds: Int? = null, val stale: Boolean = false, val empty: Boolean = false,
    val activityIndex: InsightActivityDto? = null, val fastest: InsightFastestDto? = null,
    val topWords: List<InsightWordDto> = emptyList(), val mostReacted: InsightReactedDto? = null,
    val highlights: List<String> = emptyList())

@Serializable internal data class InsightActivityDto(val score: Int, val deltaFromPreviousMonth: Int? = null,
    val participationRate: Double, val onTimeRate: Double, val interactionRate: Double)
@Serializable internal data class InsightFastestDto(val userId: Long, val nickname: String,
    val characterKey: String? = null, val profileImageUrl: String? = null, val firstCount: Int,
    val averageResponseSeconds: Long, val fastestSingleSeconds: Long)
@Serializable internal data class InsightWordDto(val word: String, val count: Int, val memberCount: Int)
@Serializable internal data class InsightReactedDto(val submissionId: Long, val missionId: Long,
    val missionContent: String, val authorNickname: String, val reactionCount: Int,
    val commentCount: Int, val topSticker: String? = null)
