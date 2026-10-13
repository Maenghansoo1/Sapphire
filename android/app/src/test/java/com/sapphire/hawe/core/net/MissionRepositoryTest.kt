package com.sapphire.hawe.core.net

import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.PokeKind
import com.sapphire.hawe.core.model.supportsTemplate
import java.net.ServerSocket
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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
            // 슬라이더 매체는 알지만 모르는 틀(나중에 서버가 켤 새 틀)은 "앱 업데이트" 안내 대상
            assertEquals(MissionType.SLIDER, mission.type)
            assertFalse(supportsTemplate(mission.template))
            assertNull(mission.spec.seconds)
            assertEquals(MissionType.WRITE, mediaToType("TEXT"))
            assertEquals(MissionType.PHOTO, mediaToType("PHOTO"))
            assertEquals(MissionType.SLIDER, mediaToType("SLIDER"))
            assertNull(mediaToType("VOICE"))  // 음성은 없앴다 — 예전 서버가 보내도 "열 수 없는 미션"
            assertNull(mediaToType("DICE"))
            // 예전 음성 미션 행은 서버가 media: null 로 보내도 읽혀야 한다(목록 전체가 실패하면 안 됨)
            server.enqueue(MockResponse().setBody("""{"date":"2026-10-12","dailyMissionCount":1,"missions":[{
                "missionId":12,"groupId":7,"slotNo":1,"number":2,"content":"예전 미션","category":null,
                "template":"VOICE_LINE","media":null,"publishedAt":"2026-10-12T09:00:00+09:00",
                "deadlineAt":"2026-10-12T23:00:00+09:00","closesAt":"2026-10-13T09:00:00+09:00",
                "submittedByMe":false,"members":[]}]}"""))
            assertNull(repo.current(7L, 8L).mission!!.type)
        }
    }

    @Test fun mapsPokedByMeAndIgnoresUnknownKinds() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"date":"2026-10-13","dailyMissionCount":1,"missions":[{
                "missionId":10,"groupId":7,"slotNo":1,"number":3,"content":"주제","category":"일상",
                "template":"PHOTO_NOW","media":"PHOTO","publishedAt":"2026-10-13T09:00:00+09:00",
                "deadlineAt":"2026-10-13T23:00:00+09:00","closesAt":"2026-10-14T09:00:00+09:00","submittedByMe":false,
                "members":[{"userId":1,"submitted":false,"pokedByMe":["poke"]},
                  {"userId":2,"submitted":false,"pokedByMe":["poke","fling"]},
                  {"userId":3,"submitted":false,"pokedByMe":["hug"]},
                  {"userId":4,"submitted":true,"pokedByMe":[]},
                  {"userId":5,"submitted":false}]}]}"""))
            val mission = MissionRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore())).current(7L, 9L).mission!!
            assertEquals(mapOf(1L to setOf(PokeKind.POKE), 2L to setOf(PokeKind.POKE, PokeKind.FLING)), mission.pokedByMe)
            assertEquals(mapOf(1L to false, 2L to false, 3L to false, 4L to true, 5L to false), mission.members)
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

    @Test fun mapsNewTemplateParams() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"date":"2026-10-13","dailyMissionCount":2,"missions":[{
                "missionId":20,"groupId":7,"slotNo":1,"number":5,"content":"짜장면 vs 짬뽕","category":"일상",
                "template":"TEXT_BALANCE","media":"TEXT","params":{"optionA":"짜장면","optionB":"짬뽕"},
                "publishedAt":"2026-10-13T09:00:00+09:00","deadlineAt":"2026-10-13T23:00:00+09:00",
                "closesAt":"2026-10-14T09:00:00+09:00","submittedByMe":false,"members":[]}]}"""))
            val repo = MissionRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            val balance = repo.current(7L, 8L).mission!!
            assertEquals(MissionType.WRITE, balance.type)
            assertEquals("TEXT_BALANCE", balance.template)
            assertEquals("짜장면", balance.spec.optionA)
            assertEquals("짬뽕", balance.spec.optionB)
            assertTrue(supportsTemplate(balance.template))
            server.takeRequest()

            // 지난 미션 한 개(달력에서 연 결과 화면용)
            server.enqueue(MockResponse().setBody("""{"missionId":21,"groupId":7,"slotNo":2,"number":6,
                "content":"민트초코, 어디쯤?","category":"일상","template":"SLIDER_TASTE","media":"SLIDER",
                "params":{"topic":"민트초코","left":"별로","right":"최고"},
                "publishedAt":"2026-10-10T09:00:00+09:00","deadlineAt":"2026-10-10T23:00:00+09:00",
                "closesAt":"2026-10-11T09:00:00+09:00","submittedByMe":true,"members":[{"userId":8,"submitted":true}]}"""))
            val taste = repo.mission(21L)
            assertEquals("/api/missions/21", server.takeRequest().path)
            assertEquals(MissionType.SLIDER, taste.type)
            assertEquals("민트초코", taste.spec.topic)
            assertEquals("민트초코", taste.spec.sliderQuestion)
            assertEquals("별로", taste.spec.left)
            assertEquals("최고", taste.spec.right)
            assertNull(taste.spec.question)
            assertTrue(taste.submittedByMe)
        }
    }

    @Test fun sliderQuestionPrefersQuestion() {
        val spec = paramsToSpec(buildJsonObject {
            put("question", JsonPrimitive("오늘 컨디션"))
            put("left", JsonPrimitive("방전"))
            put("right", JsonPrimitive("풀충전"))
        })
        assertEquals("오늘 컨디션", spec.sliderQuestion)
        assertEquals("방전", spec.left)
        assertNull(spec.optionA)
    }
}
