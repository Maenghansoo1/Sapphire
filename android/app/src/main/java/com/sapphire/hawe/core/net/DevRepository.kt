package com.sapphire.hawe.core.net

import kotlinx.serialization.json.JsonObject

class DevRepository(private val api: ApiClient) {
    suspend fun status(): DevClockStatus = api.get("/api/dev/status", auth = false)
    suspend fun setClock(now: String? = null, plusSeconds: Long? = null,
        publish: Boolean = true): DevClockStatus =
        api.put("/api/dev/clock", DevClockRequest(now, plusSeconds, publish), auth = false)
    suspend fun resetClock(): DevClockStatus = api.deleteReturning("/api/dev/clock", auth = false)
    suspend fun publish(groupId: Long? = null, template: String? = null): DevPublishResponse =
        api.post("/api/dev/missions/publish", DevPublishRequest(groupId, template), auth = false)
    suspend fun deadlineNow(missionId: Long): JsonObject =
        api.post("/api/dev/missions/$missionId/deadline-now", emptyMap<String, String>(), auth = false)
    suspend fun groupDeadlineNow(groupId: Long): JsonObject =
        api.post("/api/dev/groups/$groupId/deadline-now", emptyMap<String, String>(), auth = false)
    suspend fun submissionsOf(missionId: Long): List<DevSubmission> =
        api.get("/api/dev/missions/$missionId/submissions", auth = false)
    suspend fun submitAs(missionId: Long, userId: Long): JsonObject =
        api.post("/api/dev/missions/$missionId/submit", DevUserRequest(userId), auth = false)
    suspend fun submitMissing(missionId: Long): List<DevSubmitResult> =
        api.post("/api/dev/missions/$missionId/submit-missing", emptyMap<String, String>(), auth = false)
    suspend fun poke(groupId: Long, fromUserId: Long, toUserId: Long, type: String,
        missionId: Long? = null): JsonObject = api.post("/api/dev/groups/$groupId/poke",
        DevActorRequest(fromUserId, toUserId, type, missionId), auth = false)
    suspend fun react(submissionId: Long, userId: Long, stickerType: String): JsonObject =
        api.post("/api/dev/submissions/$submissionId/react", DevReactionRequest(userId, stickerType), auth = false)
    suspend fun comment(submissionId: Long, userId: Long, content: String): JsonObject =
        api.post("/api/dev/submissions/$submissionId/comment", DevCommentRequest(userId, content), auth = false)
    suspend fun regenerateInsight(groupId: Long, month: String): JsonObject =
        api.post("/api/dev/groups/$groupId/insights/regenerate?month=$month", emptyMap<String, String>(), auth = false)
    suspend fun updateSettings(groupId: Long, dailyMissionCount: Int, slotPublishTimes: List<String>): JsonObject =
        api.patch("/api/dev/groups/$groupId/settings", DevSettingsRequest(dailyMissionCount,
            slotPublishTimes.take(dailyMissionCount).mapIndexed { index, time -> DevSlotRequest(index + 1, time) }),
            auth = false)
    suspend fun users(): List<DevUser> = api.get("/api/dev/users", auth = false)
    suspend fun loginAs(userId: Long): TokenResponseDto =
        api.post("/api/dev/login-as", DevUserRequest(userId), auth = false)
    suspend fun seed(days: Int, memberCount: Int, dailyMissionCount: Int,
        relationType: String = "FRIEND"): DevSeedResult =
        api.post("/api/dev/seed", DevSeedRequest(days, memberCount, dailyMissionCount, relationType), auth = false)
    suspend fun deleteGroup(groupId: Long) = api.delete("/api/dev/groups/$groupId", auth = false)
    suspend fun reset(): DevResetResult =
        api.post("/api/dev/reset", emptyMap<String, String>(), auth = false)
}
