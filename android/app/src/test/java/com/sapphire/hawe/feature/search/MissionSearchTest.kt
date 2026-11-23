package com.sapphire.hawe.feature.search

import com.sapphire.hawe.core.model.MissionSearchResult
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.SearchTerm
import com.sapphire.hawe.core.net.MissionSearchDto
import com.sapphire.hawe.core.net.NetworkException
import com.sapphire.hawe.core.net.toModel
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MissionSearchTest {
    private fun result(id: Long, terms: List<SearchTerm> = listOf(SearchTerm("고양이", false)), submitted: Boolean = true) =
        MissionSearchResult(id, 1, "우리 가족", "고양이 찍기", MissionType.PHOTO, "PHOTO_NOW", LocalDate.of(2026, 11, 10),
            terms, terms.any { it.viaExpansion }, submitted)

    // ---- 검색어 검사 ----

    @Test fun queryIsCheckedLikeTheServer() {
        assertEquals(QueryCheck.Blank, checkSearchQuery(""))
        assertEquals(QueryCheck.Blank, checkSearchQuery("   "))
        assertEquals(QueryCheck.TooShort, checkSearchQuery("코"))
        assertEquals(QueryCheck.TooShort, checkSearchQuery("!!"))  // 특수문자만이면 남는 글자가 없어요
        assertEquals(QueryCheck.Ok("코뿔"), checkSearchQuery("코뿔"))
        assertEquals(QueryCheck.Ok("고양이 사진"), checkSearchQuery("  고양이!!   사진 "))
        assertEquals(QueryCheck.Ok("cat 2024"), checkSearchQuery("CAT_2024"))
        assertTrue(checkSearchQuery("가".repeat(50)) is QueryCheck.Ok)
        assertEquals(QueryCheck.TooLong, checkSearchQuery("가".repeat(51)))
    }

    // ---- 이유 문구·열기·날짜 ----

    @Test fun reasonOnlyForExpansionMatches() {
        assertEquals("‘우산’ 때문에 찾았어요",
            searchReason(result(1, listOf(SearchTerm("비", false), SearchTerm("우산", true), SearchTerm("장마", true)))))
        assertNull(searchReason(result(2, listOf(SearchTerm("고양이", false)))))
        assertNull(searchReason(result(3, emptyList())))
    }

    @Test fun openingFollowsTheLockRule() {
        assertNull(searchOpenBlock(result(1), groupExists = true))
        assertEquals("내가 올리지 않은 미션이라 다른 사람 답은 볼 수 없어요", searchOpenBlock(result(1, submitted = false), true))
        assertEquals("더 이상 볼 수 없는 그룹이에요", searchOpenBlock(result(1), groupExists = false))
    }

    @Test fun datesShowYearOnlyWhenNotThisYear() {
        val today = LocalDate.of(2026, 11, 23)
        assertEquals("11월 10일", searchDateText(LocalDate.of(2026, 11, 10), today))
        assertEquals("2025년 1월 3일", searchDateText(LocalDate.of(2025, 1, 3), today))
        assertEquals("고양이 찍기, 우리 가족, 11월 10일, 내가 올리지 않은 미션, ‘냥이’ 때문에 찾았어요",
            searchItemDescription(result(1, listOf(SearchTerm("냥이", true)), submitted = false), today))
    }

    @Test fun serverResponseMaps() {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val dtos = json.decodeFromString<List<MissionSearchDto>>("""
            [{"missionId":101,"groupId":3,"groupName":"우리 가족","content":"친구에게 우산 편지","template":"TEXT_FILL_BLANK",
              "missionDate":"2026-11-10","matchedTerms":[{"term":"우산","viaExpansion":true}],"expanded":true,"submitted":true},
             {"missionId":102,"groupId":3,"content":"날짜가 깨짐","missionDate":"???"},
             {"missionId":103,"groupId":3,"content":"모르는 틀","template":"NEW_THING","missionDate":"2026-11-11"}]
        """.trimIndent())
        val models = dtos.mapNotNull { it.toModel() }
        assertEquals(listOf(101L, 103L), models.map { it.missionId })  // 날짜를 못 읽는 항목만 버려요
        assertEquals(MissionType.WRITE, models[0].type)
        assertEquals(listOf(SearchTerm("우산", true)), models[0].matchedTerms)
        assertTrue(models[0].expanded)
        assertNull(models[1].type)  // 앱이 모르는 틀이어도 결과는 보여요
    }

    // ---- 화면 상태 ----

    @Test fun typingWaitsThenSearchesOnlyTheLatestQuery() = runTest {
        val calls = mutableListOf<Pair<String, Long?>>()
        val controller = MissionSearchController(this, { q, g -> calls += q to g; listOf(result(1)) })
        assertEquals(SearchState.Idle, controller.state)

        controller.onQueryChange("코")
        assertEquals(SearchState.TooShort, controller.state)
        controller.onQueryChange("고양")
        advanceTimeBy(200)
        controller.onQueryChange("고양이")
        assertEquals(SearchState.Loading("고양이"), controller.state)  // 기다리는 동안 안내 대신 로딩
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS - 1)
        assertTrue(calls.isEmpty())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("고양이" to null), calls)  // 치는 중이던 "고양"은 안 찾아요
        assertEquals(SearchState.Loaded("고양이", listOf(result(1))), controller.state)
    }

    @Test fun submitAndGroupChangeSearchRightAway() = runTest {
        val calls = mutableListOf<Pair<String, Long?>>()
        val controller = MissionSearchController(this, { q, g -> calls += q to g; emptyList() })
        controller.onQueryChange("비 오는 날")
        controller.onSubmit()
        runCurrent()
        assertEquals(listOf("비 오는 날" to null), calls)
        assertEquals(SearchState.Empty("비 오는 날"), controller.state)
        controller.selectGroup(5)
        runCurrent()
        assertEquals("비 오는 날" to 5L, calls.last())
        controller.selectGroup(5)  // 같은 그룹이면 다시 안 찾아요
        runCurrent()
        assertEquals(2, calls.size)
        advanceUntilIdle()
        assertEquals(2, calls.size)  // 검색 버튼으로 이미 찾았으니 기다렸다 한 번 더 찾지 않아요
    }

    @Test fun failuresSayWhyAndRetryWorks() = runTest {
        var fail: Exception? = NetworkException(IOException("offline"))
        val controller = MissionSearchController(this, { _, _ -> fail?.let { throw it }; listOf(result(1)) })
        controller.onQueryChange("고양이")
        controller.onSubmit()
        runCurrent()
        assertEquals(SearchState.Failed("고양이", offline = true), controller.state)
        fail = IllegalStateException("server")
        controller.retry()
        runCurrent()
        assertEquals(SearchState.Failed("고양이", offline = false), controller.state)
        fail = null
        controller.retry()
        runCurrent()
        assertTrue(controller.state is SearchState.Loaded)
    }

    @Test fun slowOldResultNeverOverwritesNewQuery() = runTest {
        val slow = CompletableDeferred<List<MissionSearchResult>>()
        val controller = MissionSearchController(this, { q, _ -> if (q == "고양이") slow.await() else listOf(result(2)) })
        controller.onQueryChange("고양이")
        controller.onSubmit()
        runCurrent()
        controller.onQueryChange("강아지")
        controller.onSubmit()
        runCurrent()
        slow.complete(listOf(result(1)))  // 늦게 온 옛 결과
        advanceUntilIdle()
        assertEquals(SearchState.Loaded("강아지", listOf(result(2))), controller.state)
    }

    @Test fun clearingTheQueryGoesBackToIdle() = runTest {
        val controller = MissionSearchController(this, { _, _ -> listOf(result(1)) })
        controller.onQueryChange("고양이")
        controller.onSubmit()
        runCurrent()
        controller.onQueryChange("")
        assertEquals(SearchState.Idle, controller.state)
        controller.onQueryChange("가".repeat(51))
        assertEquals(SearchState.TooLong, controller.state)
    }
}
