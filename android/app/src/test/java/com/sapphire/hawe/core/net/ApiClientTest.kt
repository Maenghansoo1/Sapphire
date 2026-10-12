package com.sapphire.hawe.core.net

import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class ApiClientTest {
    @Test fun deleteAcceptsEmptyBodyAndRefreshes() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(error("TOKEN_EXPIRED"))
            server.enqueue(MockResponse().setBody("""{"accessToken":"new","refreshToken":"next"}"""))
            server.enqueue(MockResponse().setResponseCode(204))
            val tokens = InMemoryTokenStore().apply { save("old", "refresh") }
            ApiClient(server.url("/").toString(), tokens).delete("/api/groups/2/members/me")
            assertEquals("DELETE", server.takeRequest().method)
            assertEquals("/api/auth/refresh", server.takeRequest().path)
            val retried = server.takeRequest()
            assertEquals("DELETE", retried.method)
            assertEquals("Bearer new", retried.getHeader("Authorization"))
        }
    }
    private val me = """{"id":1,"email":"a@b.com","nickname":"Ada","createdAt":"2026-09-28T09:00:00+09:00","extra":true}"""
    private fun error(code: String) = MockResponse().setResponseCode(401)
        .setBody("""{"code":"$code","message":"failure"}""")

    @Test fun parsesResponseAndSendsJson() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(me))
            server.enqueue(MockResponse().setBody(me))
            val api = ApiClient(server.url("/").toString(), InMemoryTokenStore())
            assertEquals("Ada", api.get<MeDto>("/api/me", auth = false).nickname)
            val get = server.takeRequest()
            assertNull(get.getHeader("Authorization"))
            assertEquals("/api/me", get.path)
            api.post<LoginRequestDto, MeDto>("/api/auth/login", LoginRequestDto("a@b.com", "pass"), auth = false)
            val post = server.takeRequest()
            assertTrue(post.getHeader("Content-Type")!!.startsWith("application/json"))
            assertTrue(post.body.readUtf8().contains("\"password\":\"pass\""))
        }
    }

    @Test fun errorsAndNetworkFailure() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(409).setBody("""{"code":"EMAIL_TAKEN","message":"taken"}"""))
            server.enqueue(MockResponse().setResponseCode(502).setBody("bad gateway"))
            val api = ApiClient(server.url("/").toString(), InMemoryTokenStore())
            val first = assertThrows(ApiException::class.java) { runBlocking { api.get<MeDto>("/a", false) } }
            assertEquals(409, first.status)
            assertEquals("EMAIL_TAKEN", first.code)
            assertEquals("taken", first.message)
            assertEquals("HTTP_502", assertThrows(ApiException::class.java) {
                runBlocking { api.get<MeDto>("/b", false) }
            }.code)
        }
        val port = ServerSocket(0).use { it.localPort }
        val api = ApiClient("http://127.0.0.1:$port", InMemoryTokenStore())
        assertThrows(NetworkException::class.java) { runBlocking { api.get<MeDto>("/api/me") } }
        Unit
    }

    @Test fun expiredTokenRefreshesRotatesAndRetries() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(error("TOKEN_EXPIRED"))
            server.enqueue(MockResponse().setBody("""{"accessToken":"new","refreshToken":"newRefresh"}"""))
            server.enqueue(MockResponse().setBody(me))
            val tokens = InMemoryTokenStore().apply { save("old", "oldRefresh") }
            val api = ApiClient(server.url("/").toString(), tokens)
            assertEquals("Ada", api.get<MeDto>("/api/me").nickname)
            assertEquals("Bearer old", server.takeRequest().getHeader("Authorization"))
            val refresh = server.takeRequest()
            assertEquals("/api/auth/refresh", refresh.path)
            assertTrue(refresh.body.readUtf8().contains("oldRefresh"))
            assertNull(refresh.getHeader("Authorization"))
            assertEquals("Bearer new", server.takeRequest().getHeader("Authorization"))
            assertEquals("new", tokens.accessToken)
            assertEquals("newRefresh", tokens.refreshToken)
        }
    }

    @Test fun concurrentExpiryRefreshesOnlyOnce() = runBlocking {
        MockWebServer().use { server ->
            val refreshCount = AtomicInteger()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path == "/api/auth/refresh" -> {
                        refreshCount.incrementAndGet()
                        MockResponse().setBody("""{"accessToken":"new","refreshToken":"newRefresh"}""")
                    }
                    request.getHeader("Authorization") == "Bearer old" -> error("TOKEN_EXPIRED")
                    else -> MockResponse().setBody(me)
                }
            }
            val tokens = InMemoryTokenStore().apply { save("old", "oldRefresh") }
            val api = ApiClient(server.url("/").toString(), tokens)
            val results = (1..3).map { async { api.get<MeDto>("/api/me") } }.awaitAll()
            assertEquals(3, results.size)
            assertEquals(1, refreshCount.get())
        }
    }

    @Test fun invalidTokenAndFailedRefreshClearOnce() = runBlocking {
        for (responses in listOf(listOf(error("INVALID_TOKEN")),
            listOf(error("TOKEN_EXPIRED"), error("INVALID_TOKEN")),
            listOf(error("TOKEN_EXPIRED"), MockResponse().setBody("""{"accessToken":"new","refreshToken":"r2"}"""), error("INVALID_TOKEN")))) {
            MockWebServer().use { server ->
                responses.forEach(server::enqueue)
                val tokens = InMemoryTokenStore().apply { save("old", "r1") }
                var expired = 0
                val api = ApiClient(server.url("/").toString(), tokens, onSessionExpired = { expired++ })
                assertThrows(ApiException::class.java) { runBlocking { api.get<MeDto>("/api/me") } }
                assertNull(tokens.accessToken)
                assertNull(tokens.refreshToken)
                assertEquals(1, expired)
                assertEquals(responses.size, server.requestCount)
            }
        }
    }

    @Test fun unauthenticated401KeepsSession() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(error("INVALID_CREDENTIALS"))
            val tokens = InMemoryTokenStore().apply { save("a", "r") }
            var expired = 0
            val api = ApiClient(server.url("/").toString(), tokens, onSessionExpired = { expired++ })
            assertEquals("INVALID_CREDENTIALS", assertThrows(ApiException::class.java) {
                runBlocking { api.post<LoginRequestDto, TokenResponseDto>("/api/auth/login", LoginRequestDto("a", "b"), false) }
            }.code)
            assertEquals("a", tokens.accessToken)
            assertEquals(0, expired)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun refreshNetworkFailureKeepsTokens() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(error("TOKEN_EXPIRED"))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val tokens = InMemoryTokenStore().apply { save("old", "refresh") }
            var expired = 0
            val api = ApiClient(server.url("/").toString(), tokens,
                http = OkHttpClient.Builder().retryOnConnectionFailure(false).build(),
                onSessionExpired = { expired++ })
            assertThrows(NetworkException::class.java) { runBlocking { api.get<MeDto>("/api/me") } }
            assertEquals("old", tokens.accessToken)
            assertEquals("refresh", tokens.refreshToken)
            assertEquals(0, expired)
        }
    }
}
