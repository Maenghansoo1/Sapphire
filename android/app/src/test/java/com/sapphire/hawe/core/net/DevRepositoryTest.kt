package com.sapphire.hawe.core.net

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class DevRepositoryTest {
    @Test fun clockUsersLoginAndPublishUseUnauthenticatedContract() = runBlocking {
        MockWebServer().use { server ->
            val tokens = InMemoryTokenStore().apply { save("old", "refresh") }
            val dev = DevRepository(ApiClient(server.url("/").toString(), tokens))
            val clock = """{"realNow":"2026-10-16T09:00+09:00","serverNow":"2026-10-17T09:00+09:00",
                "offsetSeconds":86400,"offsetActive":true}"""
            repeat(3) { server.enqueue(MockResponse().setBody(clock)) }
            server.enqueue(MockResponse().setBody("""[{"userId":7,"email":"dev1@hawe.dev","nickname":"개발자1"}]"""))
            server.enqueue(MockResponse().setBody("""{"accessToken":"new","refreshToken":"next"}"""))
            server.enqueue(MockResponse().setBody("""{"created":[{"missionId":9,"groupId":4,
                "slotNo":1,"template":"TEXT_ONE_WORD","content":"test"}]}"""))
            assertEquals(86400, dev.status().offsetSeconds)
            dev.setClock(now = "2026-10-17T09:00:00+09:00")
            dev.resetClock()
            assertEquals(7, dev.users().single().userId)
            assertEquals("new", dev.loginAs(7).accessToken)
            assertEquals(9, dev.publish(4, "TEXT_ONE_WORD").created.single().missionId)
            val paths = listOf("/api/dev/status", "/api/dev/clock", "/api/dev/clock", "/api/dev/users",
                "/api/dev/login-as", "/api/dev/missions/publish")
            val methods = listOf("GET", "PUT", "DELETE", "GET", "POST", "POST")
            paths.indices.forEach { index ->
                val request = server.takeRequest()
                assertEquals(paths[index], request.path)
                assertEquals(methods[index], request.method)
                assertNull(request.getHeader("Authorization"))
                if (index == 1) assertTrue(request.body.readUtf8().contains("2026-10-17T09:00:00+09:00"))
                if (index == 4) assertTrue(request.body.readUtf8().contains("\"userId\":7"))
                if (index == 5) assertTrue(request.body.readUtf8().contains("\"template\":\"TEXT_ONE_WORD\""))
            }
        }
    }

    @Test fun actionRoutesBodiesAndErrorsFollowServerContract() = runBlocking {
        MockWebServer().use { server ->
            val dev = DevRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            repeat(2) { server.enqueue(MockResponse().setBody("{}")) }
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody("{}"))
            server.enqueue(MockResponse().setBody("[]"))
            repeat(4) { server.enqueue(MockResponse().setBody("{}")) }
            server.enqueue(MockResponse().setBody("{}"))
            server.enqueue(MockResponse().setBody("""{"groupId":4,"ownerUserId":7}"""))
            server.enqueue(MockResponse().setBody("""{"deletedGroups":1}"""))
            server.enqueue(MockResponse().setResponseCode(204))
            server.enqueue(MockResponse().setResponseCode(409).setBody("""{"code":"ALREADY_SUBMITTED","message":"done"}"""))
            dev.deadlineNow(9); dev.groupDeadlineNow(4); dev.submissionsOf(9)
            dev.submitAs(9, 7); dev.submitMissing(9); dev.poke(4, 7, 8, "poke", 9)
            dev.react(12, 7, "heart"); dev.comment(12, 7, "hello")
            dev.regenerateInsight(4, "2026-10")
            dev.updateSettings(4, 2, listOf("09:00", "12:00"))
            assertEquals(4, dev.seed(2, 2, 1).groupId)
            assertEquals(1, dev.reset().deletedGroups)
            dev.deleteGroup(4)
            try { dev.submitAs(9, 7); fail("expected API error") }
            catch (error: ApiException) { assertEquals("ALREADY_SUBMITTED", error.code) }
            val paths = listOf("/api/dev/missions/9/deadline-now", "/api/dev/groups/4/deadline-now",
                "/api/dev/missions/9/submissions", "/api/dev/missions/9/submit",
                "/api/dev/missions/9/submit-missing", "/api/dev/groups/4/poke",
                "/api/dev/submissions/12/react", "/api/dev/submissions/12/comment",
                "/api/dev/groups/4/insights/regenerate?month=2026-10", "/api/dev/groups/4/settings",
                "/api/dev/seed", "/api/dev/reset", "/api/dev/groups/4", "/api/dev/missions/9/submit")
            paths.forEachIndexed { index, path ->
                val request = server.takeRequest()
                assertEquals(path, request.path)
                assertNull(request.getHeader("Authorization"))
                if (index == 3) assertTrue(request.body.readUtf8().contains("\"userId\":7"))
                if (index == 6) assertTrue(request.body.readUtf8().contains("\"stickerType\":\"heart\""))
                if (index == 9) assertTrue(request.body.readUtf8().contains("\"dailyMissionCount\":2"))
            }
        }
    }
}
