package com.sapphire.hawe.core.model

import java.time.LocalDate

/** 검색 결과에서 실제로 겹친 낱말. [viaExpansion] 이 true 면 AI 가 넓혀 준 낱말이에요(TODO_2/B2). */
data class SearchTerm(val term: String, val viaExpansion: Boolean)

/**
 * 지난 미션 검색 결과 한 줄(TODO_2/B3, 서버 `GET /api/search/missions`). 점수는 없어요.
 * [content] 의 대상 자리는 서버가 이미 "친구"로 바꿔 줘요. [type] 은 앱이 모르는 틀이면 null.
 */
data class MissionSearchResult(
    val missionId: Long,
    val groupId: Long,
    val groupName: String,
    val content: String,
    val type: MissionType?,
    val template: String,
    val missionDate: LocalDate,
    val matchedTerms: List<SearchTerm>,
    val expanded: Boolean,
    val submitted: Boolean,
)
