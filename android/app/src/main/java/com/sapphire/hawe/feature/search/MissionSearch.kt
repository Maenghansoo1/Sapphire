package com.sapphire.hawe.feature.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sapphire.hawe.core.model.MissionSearchResult
import com.sapphire.hawe.core.net.NetworkException
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 서버(`MissionSearchService.normalize`)와 같은 길이 규칙 */
const val SEARCH_MIN_LENGTH = 2
const val SEARCH_MAX_LENGTH = 50
/** 글자를 칠 때마다 부르지 않고, 멈춘 뒤 이만큼 기다렸다가 찾아요. 키보드 검색 버튼은 바로 찾아요. */
const val SEARCH_DEBOUNCE_MILLIS = 400L

sealed interface QueryCheck {
    data object Blank : QueryCheck
    data object TooShort : QueryCheck
    data object TooLong : QueryCheck
    data class Ok(val query: String) : QueryCheck
}

/**
 * 서버와 같은 방식으로 다듬어서(글자·숫자·띄어쓰기만, 공백 하나로, 소문자) 길이를 봐요.
 * 서버가 400 을 줄 검색어는 미리 걸러 요청하지 않아요.
 */
fun checkSearchQuery(raw: String): QueryCheck {
    val normalized = raw.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").replace(Regex("\\s+"), " ").trim().lowercase(Locale.ROOT)
    val length = normalized.codePointCount(0, normalized.length)
    return when {
        length == 0 -> if (raw.isBlank()) QueryCheck.Blank else QueryCheck.TooShort
        length < SEARCH_MIN_LENGTH -> QueryCheck.TooShort
        length > SEARCH_MAX_LENGTH -> QueryCheck.TooLong
        else -> QueryCheck.Ok(normalized)
    }
}

sealed interface SearchState {
    /** 아직 아무것도 안 쳤어요 */
    data object Idle : SearchState
    data object TooShort : SearchState
    data object TooLong : SearchState
    data class Loading(val query: String) : SearchState
    data class Loaded(val query: String, val results: List<MissionSearchResult>) : SearchState
    data class Empty(val query: String) : SearchState
    /** [offline] 이면 네트워크 문제, 아니면 서버 문제 */
    data class Failed(val query: String, val offline: Boolean) : SearchState
}

/**
 * 검색 화면 상태(TODO_2/B3). 치는 동안은 [SEARCH_DEBOUNCE_MILLIS] 만큼 멈췄을 때, 검색 버튼·그룹 바꾸기·다시 시도는 바로 찾아요.
 * 새 요청이 오면 진행 중이던 요청은 취소해서 **마지막 검색어 결과만** 보여요. 메인 스레드에서만 불러 주세요.
 */
class MissionSearchController(
    private val scope: CoroutineScope,
    private val search: suspend (query: String, groupId: Long?) -> List<MissionSearchResult>,
    private val debounceMillis: Long = SEARCH_DEBOUNCE_MILLIS,
) {
    var query by mutableStateOf("")
        private set
    /** null 이면 내 모든 그룹 */
    var groupId by mutableStateOf<Long?>(null)
        private set
    var state by mutableStateOf<SearchState>(SearchState.Idle)
        private set
    private var job: Job? = null

    fun onQueryChange(value: String) {
        if (value == query) return
        query = value
        run(debounceMillis)
    }

    fun onSubmit() = run(0)

    fun selectGroup(id: Long?) {
        if (id == groupId) return
        groupId = id
        run(0)
    }

    fun retry() = run(0)

    private fun run(waitMillis: Long) {
        job?.cancel()
        val query = when (val check = checkSearchQuery(query)) {
            QueryCheck.Blank -> { state = SearchState.Idle; return }
            QueryCheck.TooShort -> { state = SearchState.TooShort; return }
            QueryCheck.TooLong -> { state = SearchState.TooLong; return }
            is QueryCheck.Ok -> check.query
        }
        val group = groupId
        // 기다리는 동안 "두 글자 이상" 같은 안내가 남지 않게. 이미 보이는 결과는 새 결과가 올 때까지 둬요
        if (state !is SearchState.Loaded) state = SearchState.Loading(query)
        job = scope.launch {
            if (waitMillis > 0) delay(waitMillis)
            state = SearchState.Loading(query)
            state = try {
                val results = search(query, group)
                if (results.isEmpty()) SearchState.Empty(query) else SearchState.Loaded(query, results)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                SearchState.Failed(query, offline = error is NetworkException)
            }
        }
    }
}

/** 확장어로 찾은 항목의 이유 문구. 원 검색어로 맞은 항목은 문구 없이 보여요. */
fun searchReason(result: MissionSearchResult): String? =
    result.matchedTerms.firstOrNull { it.viaExpansion }?.let { "‘${it.term}’ 때문에 찾았어요" }

/** 결과를 눌렀을 때 열지 못하는 이유(열 수 있으면 null). 잠금은 서버 규칙 그대로 — 내가 안 낸 미션의 답은 못 봐요. */
fun searchOpenBlock(result: MissionSearchResult, groupExists: Boolean): String? = when {
    !groupExists -> "더 이상 볼 수 없는 그룹이에요"
    !result.submitted -> "내가 올리지 않은 미션이라 다른 사람 답은 볼 수 없어요"
    else -> null
}

/** "11월 10일", 올해가 아니면 "2025년 11월 10일" */
fun searchDateText(date: LocalDate, today: LocalDate): String =
    if (date.year == today.year) "${date.monthValue}월 ${date.dayOfMonth}일"
    else "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일"

/** 화면 낭독용: 문장, 그룹, 날짜, 내가 올렸는지, 이유 */
fun searchItemDescription(result: MissionSearchResult, today: LocalDate): String = listOfNotNull(
    result.content, result.groupName.ifBlank { null }, searchDateText(result.missionDate, today),
    if (result.submitted) "내가 올린 미션" else "내가 올리지 않은 미션", searchReason(result),
).joinToString(", ")
