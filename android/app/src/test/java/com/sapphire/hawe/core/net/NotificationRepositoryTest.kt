package com.sapphire.hawe.core.net

import com.sapphire.hawe.core.model.AvatarContent
import com.sapphire.hawe.core.model.NotificationType
import com.sapphire.hawe.designsystem.assets.ProfileCharacter
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class NotificationRepositoryTest {
    @Test fun mapsListAndSkipsUnknownTypes() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"content":[
                {"id":1,"type":"poke","groupId":3,"groupName":"컴공 동기",
                 "fromUser":{"userId":7,"nickname":"서연","characterKey":"mulbangul","profileImageUrl":null},
                 "missionId":10,"submissionId":null,"body":"서연님이 콕 찔렀어요","isRead":false,
                 "createdAt":"2026-10-19T15:41:48+09:00"},
                {"id":2,"type":"topic","groupId":3,"groupName":"컴공 동기","fromUser":null,"missionId":11,
                 "isRead":true,"createdAt":"2026-10-19T09:00:00+09:00"},
                {"id":3,"type":"hug","groupId":3,"createdAt":"2026-10-19T09:00:00+09:00"}],
                "page":0,"size":50,"totalPages":1,"totalElements":3}"""))
            val items = NotificationRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore())).list()
            assertEquals("/api/notifications?page=0&size=50", server.takeRequest().path)
            assertEquals(listOf(1L, 2L), items.map { it.id })
            val poke = items[0]
            assertEquals(NotificationType.POKE, poke.type)
            assertEquals("서연", poke.from?.nickname)
            assertEquals(AvatarContent.Character(ProfileCharacter.MULBANGUL), poke.from?.avatar)
            assertEquals(Instant.parse("2026-10-19T06:41:48Z"), poke.createdAt)
            assertFalse(poke.isRead)
            assertNull(items[1].from)
            assertNull(items[1].body)
        }
    }

    @Test fun marksReadWithPatch() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(204))
            server.enqueue(MockResponse().setResponseCode(204))
            val repository = NotificationRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            repository.markRead(5)
            repository.markAllRead()
            val first = server.takeRequest(); val second = server.takeRequest()
            assertEquals("PATCH" to "/api/notifications/5/read", first.method to first.path)
            assertEquals("PATCH" to "/api/notifications/read-all", second.method to second.path)
        }
    }
}
