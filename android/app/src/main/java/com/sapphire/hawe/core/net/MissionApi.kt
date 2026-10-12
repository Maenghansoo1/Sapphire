package com.sapphire.hawe.core.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable internal data class TodayMissionsDto(val date: String, val dailyMissionCount: Int,
    val nextPublishAt: String? = null, val missions: List<MissionDto>)
@Serializable internal data class MissionDto(val missionId: Long, val groupId: Long, val slotNo: Int,
    val number: Int, val content: String, val category: String, val template: String, val media: String,
    val params: JsonObject = JsonObject(emptyMap()), val publishedAt: String, val deadlineAt: String,
    val closesAt: String, val submittedByMe: Boolean, val members: List<MissionMemberDto>)
@Serializable internal data class MissionMemberDto(val userId: Long, val submitted: Boolean)
@Serializable internal data class TodayMissionSummaryDto(val missionId: Long, val media: String,
    val content: String, val submittedCount: Int, val submittedByMe: Boolean)
