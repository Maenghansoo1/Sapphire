package com.sapphire.hawe.core.net

import com.sapphire.hawe.core.model.MissionType
import java.net.ServerSocket
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class MissionRepositoryTest {
    @Test fun mapsCurrentMissionAndParams() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"date":"2026-10-12","dailyMissionCount":1,
                "nextPublishAt":"2026-10-13T09:00:00+09:00","missions":[{
                "missionId":10,"groupId":7,"slotNo":1,"number":142,"content":"서버 주제",
                "category":"유머","template":"DRAW_A_TO_B","media":"DRAW",
                "params":{"seconds":30,"word":"고양이","a":"컵","b":"화분"},
                "publishedAt":"2026-10-12T09:00:00+09:00","deadlineAt":"2026-10-12T23:00:00+09:00",
                "closesAt":"2026-10-13T09:00:00+09:00","submittedByMe":false,
                "members":[{"userId":8,"submitted":true}]}]}"""))
            val state = MissionRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore())).current(7L, 8L)
            val mission = state.mission!!
            assertEquals(10L, mission.id)
            assertEquals(142, mission.number)
            assertEquals(MissionType.DRAW, mission.type)
            assertEquals(30, mission.spec.seconds)
            assertEquals("고양이", mission.spec.word)
            assertEquals("컵", mission.spec.drawFrom)
            assertEquals("화분", mission.spec.drawTo)
            assertEquals(mapOf(8L to true), mission.members)
            assertEquals(Instant.parse("2026-10-12T00:00:00Z"), mission.publishedAt)
            assertEquals(mission.closesAt, state.nextPublishAt)
            assertEquals("/api/groups/7/missions/today", server.takeRequest().path)
        }
    }

    @Test fun handlesEmptyAndUnknownMedia() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"date":"2026-10-12","dailyMissionCount":1,"missions":[]}"""))
            val repo = MissionRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            assertNull(repo.current(7L, 8L).mission)
            server.enqueue(MockResponse().setBody("""{"date":"2026-10-12","dailyMissionCount":1,"missions":[{
                "missionId":11,"groupId":7,"slotNo":1,"number":1,"content":"새 미션","category":"일상",
                "template":"FUTURE","media":"SLIDER","publishedAt":"2026-10-12T09:00:00+09:00",
                "deadlineAt":"2026-10-12T23:00:00+09:00","closesAt":"2026-10-13T09:00:00+09:00",
                "submittedByMe":false,"members":[]}]}"""))
            val mission = repo.current(7L, 8L).mission!!
            assertNull(mission.type)
            assertNull(mission.spec.seconds)
            assertEquals(MissionType.WRITE, mediaToType("TEXT"))
            assertEquals(MissionType.PHOTO, mediaToType("PHOTO"))
            assertNull(mediaToType("VOICE"))  // 음성은 없앴다 — 예전 서버가 보내도 "열 수 없는 미션"
        }
    }

    @Test fun propagatesServerErrors() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"code":"NOT_GROUP_MEMBER","message":"forbidden"}"""))
            server.enqueue(MockResponse().setResponseCode(404).setBody("""{"code":"GROUP_NOT_FOUND","message":"missing"}"""))
            val repo = MissionRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            val error = assertThrows(ApiException::class.java) { runBlocking { repo.current(7L, 8L) } }
            assertEquals(403, error.status)
            val missing = assertThrows(ApiException::class.java) { runBlocking { repo.current(7L, 8L) } }
            assertEquals(404, missing.status)
        }
    }

    @Test fun propagatesNetworkError() {
        val port = ServerSocket(0).use { it.localPort }
        val repo = MissionRepository(ApiClient("http://127.0.0.1:$port", InMemoryTokenStore()))
        assertThrows(NetworkException::class.java) { runBlocking { repo.current(7L, 8L) } }
    }
}
