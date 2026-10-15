package com.sapphire.hawe.core.net

import kotlinx.serialization.Serializable

@Serializable data class DevClockStatus(val realNow: String = "", val serverNow: String = "",
    val offsetSeconds: Long = 0, val offsetActive: Boolean = false, val warning: String? = null)
@Serializable internal data class DevClockRequest(val now: String? = null, val plusSeconds: Long? = null,
    val publish: Boolean = true)
@Serializable data class DevUser(val userId: Long, val email: String = "", val nickname: String = "",
    val characterKey: String? = null)
@Serializable internal data class DevUserRequest(val userId: Long)
@Serializable internal data class DevPublishRequest(val groupId: Long? = null, val template: String? = null)
@Serializable data class DevPublishedMission(val missionId: Long, val groupId: Long, val slotNo: Int,
    val template: String, val content: String)
@Serializable data class DevPublishResponse(val created: List<DevPublishedMission> = emptyList(),
    val message: String? = null)
@Serializable data class DevSubmission(val submissionId: Long, val userId: Long, val nickname: String = "",
    val template: String = "", val submittedAt: String = "", val late: Boolean = false)
@Serializable data class DevSubmitResult(val userId: Long, val ok: Boolean, val submissionId: Long? = null,
    val errorCode: String? = null)
@Serializable internal data class DevActorRequest(val fromUserId: Long, val toUserId: Long,
    val type: String, val missionId: Long? = null)
@Serializable internal data class DevReactionRequest(val userId: Long, val stickerType: String)
@Serializable internal data class DevCommentRequest(val userId: Long, val content: String)
@Serializable internal data class DevSeedRequest(val days: Int = 30, val memberCount: Int = 5,
    val dailyMissionCount: Int = 1, val relationType: String = "FRIEND")
@Serializable data class DevSeedResult(val groupId: Long, val name: String = "", val inviteCode: String = "",
    val ownerUserId: Long, val memberUserIds: List<Long> = emptyList(), val days: Int = 0,
    val missionsCreated: Int = 0, val submissionsCreated: Int = 0)
@Serializable data class DevResetResult(val deletedGroups: Int = 0)
@Serializable internal data class DevSlotRequest(val slotNo: Int, val publishTime: String)
@Serializable internal data class DevSettingsRequest(val dailyMissionCount: Int,
    val missionSlots: List<DevSlotRequest>)
