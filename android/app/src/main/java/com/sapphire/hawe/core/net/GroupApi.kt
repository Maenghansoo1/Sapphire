package com.sapphire.hawe.core.net

import kotlinx.serialization.Serializable

@Serializable internal data class GroupMemberProfileDto(val userId: Long, val nickname: String,
    val characterKey: String? = null, val profileImageUrl: String? = null)
@Serializable internal data class GroupSummaryDto(val id: Long, val name: String, val relationType: String,
    val memberCount: Int, val maxMembers: Int, val members: List<GroupMemberProfileDto>,
    val startDate: String? = null, val submittedToday: Boolean = false,
    val todayMission: TodayMissionSummaryDto? = null)
@Serializable internal data class MissionSlotDto(val slotNo: Int, val publishTime: String,
    val deadlineTime: String, val custom: Boolean)
@Serializable internal data class GroupDetailDto(val id: Long, val name: String, val ownerId: Long,
    val relationType: String, val createdAt: String, val memberCount: Int, val maxMembers: Int,
    val inviteCode: String, val inviteUrl: String, val startDate: String? = null,
    val dailyMissionCount: Int, val missionSlots: List<MissionSlotDto>, val missionCount: Long)
@Serializable internal data class GroupMemberDto(val userId: Long, val nickname: String,
    val characterKey: String? = null, val profileImageUrl: String? = null, val role: String,
    val submissionCountThisMonth: Int = 0, val stampedToday: Boolean = false)
@Serializable internal data class CreateGroupRequestDto(val name: String, val relationType: String,
    val startDate: String? = null)
@Serializable internal data class InvitePreviewDto(val groupId: Long, val name: String,
    val relationType: String, val memberCount: Int, val maxMembers: Int,
    val members: List<GroupMemberProfileDto>)
@Serializable internal data class JoinRequestDto(val code: String)
@Serializable internal data class JoinResponseDto(val groupId: Long, val name: String)
