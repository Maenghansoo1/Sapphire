package com.sapphire.hawe.core.net

import com.sapphire.hawe.core.model.PokeKind
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class PokeRepositoryTest {
    @Test fun sendsSelectedMissionId() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(201).setBody(
                """{"id":1,"toUserId":7,"type":"poke","missionId":20}"""))
            val sent = PokeRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
                .send(3L, 7L, PokeKind.POKE, 20L)
            assertEquals(20L, sent.missionId)
            assertEquals("""{"toUserId":7,"type":"poke","missionId":20}""",
                server.takeRequest().body.readUtf8())
        }
    }
    @Test fun sendsKindAndMapsResponse() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(201).setBody(
                """{"id":1,"toUserId":7,"type":"poke","missionId":10,"sentAt":"2026-10-13T15:41:48+09:00"}"""))
            val tokens = InMemoryTokenStore().apply { save("access", "refresh") }
            val sent = PokeRepository(ApiClient(server.url("/").toString(), tokens)).send(3L, 7L, PokeKind.POKE)
            assertEquals(SentPoke(7L, PokeKind.POKE, 10L), sent)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/groups/3/pokes", request.path)
            assertEquals("Bearer access", request.getHeader("Authorization"))
            assertEquals("""{"toUserId":7,"type":"poke"}""", request.body.readUtf8())
        }
    }

    @Test fun serverErrorsKeepTheirCodes() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(409)
                .setBody("""{"code":"ALREADY_POKED","message":"이미 보낸 재촉입니다"}"""))
            val repo = PokeRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            val error = assertThrows(ApiException::class.java) { runBlocking { repo.send(3L, 7L, PokeKind.POKE) } }
            assertEquals(409, error.status)
            assertEquals("ALREADY_POKED", error.code)
            assertEquals("""{"toUserId":7,"type":"poke"}""", server.takeRequest().body.readUtf8())
        }
    }

    @Test fun failureClassification() {
        fun api(status: Int, code: String) = ApiException(status, code, "")
        // 이미 보냄·이미 제출함·미션 없음은 안내 없이 상태만 맞춘다 (CONTRACT §4)
        assertEquals(PokeFailure(PokeFollowUp.MARK_SENT, null), classifyPokeFailure(api(409, "ALREADY_POKED")))
        assertEquals(PokeFailure(PokeFollowUp.REFRESH, null), classifyPokeFailure(api(409, "TARGET_ALREADY_SUBMITTED")))
        assertEquals(PokeFailure(PokeFollowUp.REFRESH, null), classifyPokeFailure(api(409, "NO_OPEN_MISSION")))
        assertEquals(PokeFailure(PokeFollowUp.REFRESH, "그룹을 나간 멤버예요"),
            classifyPokeFailure(api(404, "TARGET_NOT_GROUP_MEMBER")))
        assertEquals(PokeFailure(PokeFollowUp.LEAVE, "이 그룹의 멤버가 아니에요"),
            classifyPokeFailure(api(403, "NOT_GROUP_MEMBER")))
        assertEquals(PokeFailure(PokeFollowUp.LEAVE, "그룹을 찾을 수 없어요"), classifyPokeFailure(api(404, "GROUP_NOT_FOUND")))
        assertEquals(PokeFailure(PokeFollowUp.NONE, "나에게는 보낼 수 없어요"), classifyPokeFailure(api(400, "CANNOT_POKE_SELF")))
        assertEquals(PokeFailure(PokeFollowUp.NONE, "잠시 후 다시 시도해 주세요"), classifyPokeFailure(api(500, "INTERNAL")))
        assertEquals(PokeFailure(PokeFollowUp.NONE, "네트워크 연결을 확인해 주세요"),
            classifyPokeFailure(NetworkException(java.io.IOException("offline"))))
    }
}
