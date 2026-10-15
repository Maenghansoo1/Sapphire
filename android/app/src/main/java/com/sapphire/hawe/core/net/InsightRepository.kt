package com.sapphire.hawe.core.net

import com.sapphire.hawe.core.model.ActivityIndex
import com.sapphire.hawe.core.model.FastestMember
import com.sapphire.hawe.core.model.GroupInsight
import com.sapphire.hawe.core.model.MostReacted
import com.sapphire.hawe.core.model.Profile
import com.sapphire.hawe.core.model.Sticker
import com.sapphire.hawe.core.model.TopWord
import java.time.YearMonth

sealed interface InsightResult {
    data class Ready(val insight: GroupInsight) : InsightResult
    data class Generating(val retryAfterSeconds: Int) : InsightResult
}

class InsightRepository(private val api: ApiClient) {
    suspend fun load(groupId: Long, month: YearMonth): InsightResult {
        val dto: InsightDto = api.get("/api/groups/$groupId/insights?month=$month")
        if (dto.groupId != groupId || dto.month != month.toString())
            throw ApiException(200, "INVALID_RESPONSE", "요청한 달의 응답이 아닙니다")
        return when (dto.status) {
            "GENERATING" -> InsightResult.Generating(dto.retryAfterSeconds ?: 3)
            "READY" -> InsightResult.Ready(GroupInsight(month, dto.stale, dto.empty,
                dto.activityIndex?.let { ActivityIndex(it.score, it.deltaFromPreviousMonth,
                    it.participationRate, it.onTimeRate, it.interactionRate) },
                dto.fastest?.let { FastestMember(it.userId,
                    Profile(api.avatarOf(it.characterKey, it.profileImageUrl), it.nickname), it.firstCount,
                    it.averageResponseSeconds, it.fastestSingleSeconds) },
                dto.topWords.map { TopWord(it.word, it.count, it.memberCount) },
                dto.mostReacted?.let { MostReacted(it.submissionId, it.missionId, it.missionContent,
                    it.authorNickname, it.reactionCount, it.commentCount,
                    it.topSticker?.let(Sticker::fromKey)) }, dto.highlights))
            else -> throw ApiException(200, "INVALID_RESPONSE", "알 수 없는 인사이트 상태입니다")
        }
    }
}
