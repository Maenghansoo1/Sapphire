package com.sapphire.hawe.core.net

import com.sapphire.hawe.core.model.MissionSearchResult
import com.sapphire.hawe.core.model.SearchTerm
import java.net.URLEncoder
import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable internal data class MissionSearchDto(val missionId: Long, val groupId: Long, val groupName: String = "",
    val content: String, val template: String = "", val missionDate: String,
    val matchedTerms: List<MatchedTermDto> = emptyList(), val expanded: Boolean = false, val submitted: Boolean = false)
@Serializable internal data class MatchedTermDto(val term: String, val viaExpansion: Boolean = false)

/** 지난 미션 검색(TODO_2/B3). 검색어는 이 기기에 저장하지 않아요. */
class SearchRepository(private val api: ApiClient) {
    /** [query] 는 [com.sapphire.hawe.feature.search.checkSearchQuery] 를 통과한 2~50자. [groupId] 가 null 이면 내 모든 그룹. */
    suspend fun searchMissions(query: String, groupId: Long? = null, limit: Int = 20): List<MissionSearchResult> {
        val path = buildString {
            append("/api/search/missions?q=").append(URLEncoder.encode(query, "UTF-8"))
            if (groupId != null) append("&groupId=").append(groupId)
            append("&limit=").append(limit)
        }
        val response: List<MissionSearchDto> = api.get(path)
        return response.mapNotNull { it.toModel() }
    }
}

/** 날짜를 못 읽는 항목만 버려요(크래시 없음). */
internal fun MissionSearchDto.toModel(): MissionSearchResult? {
    val date = runCatching { LocalDate.parse(missionDate) }.getOrNull() ?: return null
    return MissionSearchResult(missionId, groupId, groupName, content, mediaToType(template.substringBefore('_')),
        template, date, matchedTerms.map { SearchTerm(it.term, it.viaExpansion) }, expanded, submitted)
}
