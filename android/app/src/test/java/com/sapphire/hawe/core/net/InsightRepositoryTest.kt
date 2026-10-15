package com.sapphire.hawe.core.net

import com.sapphire.hawe.core.model.Sticker
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class InsightRepositoryTest {
    private val month = YearMonth.of(2026, 10)

    @Test fun mapsReadyAndRequestPath() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"groupId":7,"month":"2026-10","status":"READY",
                "stale":true,"empty":false,"activityIndex":{"score":83,"deltaFromPreviousMonth":-3,
                "participationRate":0.8,"onTimeRate":0.75,"interactionRate":0.5},
                "fastest":{"userId":9,"nickname":"하늘","characterKey":"mongsil","firstCount":4,
                "averageResponseSeconds":72,"fastestSingleSeconds":12},
                "topWords":[{"word":"하늘","count":5,"memberCount":3}],
                "mostReacted":{"submissionId":11,"missionId":12,"missionContent":"오늘의 하늘",
                "authorNickname":"봄","reactionCount":4,"commentCount":2,"topSticker":"heart"},
                "highlights":["함께했어요"]}"""))
            val result = InsightRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore())).load(7, month)
            assertEquals("/api/groups/7/insights?month=2026-10", server.takeRequest().path)
            val insight = (result as InsightResult.Ready).insight
            assertTrue(insight.stale)
            assertFalse(insight.empty)
            assertEquals(83, insight.activityIndex?.score)
            assertEquals(-3, insight.activityIndex?.delta)
            assertEquals(0.8, insight.activityIndex!!.participationRate, 0.001)
            assertEquals(9L, insight.fastest?.userId)
            assertEquals(72L, insight.fastest?.averageResponseSeconds)
            assertEquals("하늘", insight.topWords.single().word)
            assertEquals(12L, insight.mostReacted?.missionId)
            assertEquals(Sticker.HEART, insight.mostReacted?.topSticker)
            assertEquals(listOf("함께했어요"), insight.highlights)
        }
    }

    @Test fun mapsMissingSectionsAndEmptyMonth() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"groupId":7,"month":"2026-10","status":"READY","empty":true}"""))
            val insight = (InsightRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
                .load(7, month) as InsightResult.Ready).insight
            assertTrue(insight.empty)
            assertNull(insight.fastest)
            assertNull(insight.activityIndex)
            assertTrue(insight.topWords.isEmpty())
            assertTrue(insight.highlights.isEmpty())
        }
    }

    @Test fun mapsGeneratingAndErrors() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(202)
                .setBody("""{"groupId":7,"month":"2026-10","status":"GENERATING","retryAfterSeconds":5}"""))
            val repository = InsightRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            assertEquals(InsightResult.Generating(5), repository.load(7, month))
            for ((status, code) in listOf(400 to "VALIDATION_ERROR", 403 to "NOT_GROUP_MEMBER",
                404 to "GROUP_NOT_FOUND")) {
                server.enqueue(MockResponse().setResponseCode(status)
                    .setBody("""{"code":"$code","message":"오류"}"""))
                val error = runCatching { repository.load(7, month) }.exceptionOrNull() as ApiException
                assertEquals(status, error.status)
                assertEquals(code, error.code)
            }
        }
    }

    @Test fun rejectsUnknownStatusAndWrongMonth() = runBlocking {
        MockWebServer().use { server ->
            val repository = InsightRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            server.enqueue(MockResponse().setBody("""{"groupId":7,"month":"2026-10","status":"OTHER"}"""))
            assertEquals("INVALID_RESPONSE", (runCatching { repository.load(7, month) }
                .exceptionOrNull() as ApiException).code)
            server.enqueue(MockResponse().setBody("""{"groupId":7,"month":"2026-09","status":"READY"}"""))
            assertEquals("INVALID_RESPONSE", (runCatching { repository.load(7, month) }
                .exceptionOrNull() as ApiException).code)
        }
    }
}
