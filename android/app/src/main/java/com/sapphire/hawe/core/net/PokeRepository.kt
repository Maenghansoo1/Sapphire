package com.sapphire.hawe.core.net

import com.sapphire.hawe.core.model.PokeKind

data class SentPoke(val toUserId: Long, val kind: PokeKind, val missionId: Long)

/** 재촉에 실패한 뒤 화면이 할 일. */
enum class PokeFollowUp { MARK_SENT, REFRESH, LEAVE, NONE }

/** `message` 가 null 이면 안내 없이 조용히 처리한다(CONTRACT §4: 이미 보냄·이미 제출함). */
data class PokeFailure(val followUp: PokeFollowUp, val message: String?)

fun classifyPokeFailure(error: Throwable): PokeFailure = when ((error as? ApiException)?.code) {
    "ALREADY_POKED" -> PokeFailure(PokeFollowUp.MARK_SENT, null)
    "TARGET_ALREADY_SUBMITTED", "NO_OPEN_MISSION" -> PokeFailure(PokeFollowUp.REFRESH, null)
    "TARGET_NOT_GROUP_MEMBER" -> PokeFailure(PokeFollowUp.REFRESH, error.toUserMessage())
    "NOT_GROUP_MEMBER", "GROUP_NOT_FOUND" -> PokeFailure(PokeFollowUp.LEAVE, error.toUserMessage())
    else -> PokeFailure(PokeFollowUp.NONE, error.toUserMessage())
}

class PokeRepository(private val api: ApiClient) {
    suspend fun send(groupId: Long, toUserId: Long, kind: PokeKind, missionId: Long? = null): SentPoke {
        val dto: PokeResponseDto = api.post("/api/groups/$groupId/pokes", PokeRequestDto(toUserId, kind.wire, missionId))
        return SentPoke(dto.toUserId, PokeKind.fromWire(dto.type) ?: kind, dto.missionId)
    }
}
