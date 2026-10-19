package com.sapphire.hawe.core.net

import com.sapphire.hawe.core.model.AvatarContent
import com.sapphire.hawe.core.model.Profile
import com.sapphire.hawe.designsystem.assets.ProfileCharacter
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class AuthRepositoryTest {
    @Test fun passwordChangeRotatesTokensAndFailureKeepsOldTokens() = runBlocking {
        MockWebServer().use { server ->
            val store = InMemoryTokenStore().apply { save("old", "oldRefresh") }
            val repo = fixture(server, store)
            server.enqueue(MockResponse().setResponseCode(400)
                .setBody("""{"code":"WRONG_CURRENT_PASSWORD","message":"wrong"}"""))
            val error = assertThrows(ApiException::class.java) { runBlocking {
                repo.changePassword("wrong", "newPassword1")
            } }
            assertEquals("WRONG_CURRENT_PASSWORD", error.code)
            assertEquals("old", store.accessToken)
            assertEquals("oldRefresh", store.refreshToken)
            server.enqueue(MockResponse().setBody("""{"accessToken":"new","refreshToken":"newRefresh"}"""))
            repo.changePassword("oldPassword1", "newPassword1")
            assertEquals("new", store.accessToken)
            assertEquals("newRefresh", store.refreshToken)
            val failed = server.takeRequest()
            assertEquals("PATCH", failed.method)
            assertEquals("/api/me/password", failed.path)
            val success = server.takeRequest()
            assertEquals("Bearer old", success.getHeader("Authorization"))
            val body = success.body.readUtf8()
            assertTrue(body.contains("\"currentPassword\":\"oldPassword1\""))
            assertTrue(body.contains("\"newPassword\":\"newPassword1\""))
            server.enqueue(MockResponse().setBody(me()))
            repo.restore()
            assertEquals("Bearer new", server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test fun nicknameSaveUsesServerResponseAndRestores() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(me()))
            server.enqueue(MockResponse().setBody(me()))
            val current = Profile(AvatarContent.Character(ProfileCharacter.MONGSIL), "Old")
            val result = fixture(server).saveProfile(current, " Ada ", current.avatar) { error("unused") }
            assertNull(result.error)
            assertEquals("Ada", result.account?.profile?.nickname)
            val patch = server.takeRequest()
            assertEquals("PATCH", patch.method)
            assertEquals("/api/me/profile", patch.path)
            assertTrue(patch.body.readUtf8().contains("\"nickname\":\"Ada\""))
            assertEquals("/api/me", server.takeRequest().path)
        }
    }

    @Test fun characterChangeUsesServerIdAndRestores() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"content":[{"id":5,"name":"water","svgKey":"mulbangul"}]}"""))
            server.enqueue(MockResponse().setBody("""{"characterId":5,"name":"water","svgKey":"mulbangul"}"""))
            server.enqueue(MockResponse().setBody(me("mulbangul")))
            val current = Profile(AvatarContent.Character(ProfileCharacter.MONGSIL), "Ada")
            val result = fixture(server).saveProfile(current, "Ada",
                AvatarContent.Character(ProfileCharacter.MULBANGUL)) { error("unused") }
            assertNull(result.error)
            assertEquals(AvatarContent.Character(ProfileCharacter.MULBANGUL), result.account?.profile?.avatar)
            assertEquals("/api/characters?page=0&size=100", server.takeRequest().path)
            val patch = server.takeRequest()
            assertEquals("/api/me/character", patch.path)
            assertTrue(patch.body.readUtf8().contains("\"characterId\":5"))
            assertEquals("/api/me", server.takeRequest().path)
        }
    }

    @Test fun withdrawUsesServerDelete() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(204))
            fixture(server).withdraw()
            assertEquals("DELETE", server.takeRequest().method)
        }
    }

    @Test fun withdrawalConflictPropagates() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(409)
                .setBody("""{"code":"OWNER_MUST_TRANSFER","message":"transfer"}"""))
            val failure = assertThrows(ApiException::class.java) { runBlocking { fixture(server).withdraw() } }
            assertEquals("OWNER_MUST_TRANSFER", failure.code)
            assertEquals("DELETE", server.takeRequest().method)
        }
    }

    private val tokensJson = """{"accessToken":"a","refreshToken":"r"}"""
    private fun me(character: String? = null) = """{"id":1,"email":"ada@example.com","nickname":"Ada","characterKey":${character?.let { "\"$it\"" } ?: "null"},"createdAt":"2026-09-28T09:00:00+09:00"}"""
    private fun fixture(server: MockWebServer, tokens: InMemoryTokenStore = InMemoryTokenStore()) =
        AuthRepository(ApiClient(server.url("/").toString(), tokens), tokens)

    @Test fun loginStoresTokensAndMapsAccount() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(tokensJson))
            server.enqueue(MockResponse().setBody(me()))
            val tokens = InMemoryTokenStore()
            val account = fixture(server, tokens).login("  ADA@example.com  ", "pass ")
            assertEquals("/api/auth/login", server.takeRequest().path)
            assertEquals("Bearer a", server.takeRequest().getHeader("Authorization"))
            assertEquals("a", tokens.accessToken)
            assertEquals("r", tokens.refreshToken)
            assertEquals("ada@example.com", account.email)
            assertEquals("Ada", account.profile.nickname)
            assertEquals(AvatarContent.Character(ProfileCharacter.DEFAULT), account.profile.avatar)
            assertEquals(Instant.parse("2026-09-28T00:00:00Z"), account.joinedAt)
        }
    }

    @Test fun signupCallsEndpointsInOrderAndUsesCharacterId() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":1,"email":"ada@example.com","nickname":"Ada"}"""))
            server.enqueue(MockResponse().setBody(tokensJson))
            server.enqueue(MockResponse().setBody("""{"content":[{"id":7,"name":"flower","svgKey":"kkotnim"},{"id":5,"name":"water","svgKey":"mulbangul"}]}"""))
            server.enqueue(MockResponse().setBody("""{"characterId":5,"name":"water","svgKey":"mulbangul"}"""))
            server.enqueue(MockResponse().setBody(me("mulbangul")))
            var registered = 0
            val account = fixture(server).signUp(" ada@example.com ", "password1", " Ada ",
                AvatarContent.Character(ProfileCharacter.MULBANGUL), true, true, false, { registered++ }) { error("unused") }
            val register = server.takeRequest()
            assertEquals("/api/auth/register", register.path)
            val body = register.body.readUtf8()
            assertTrue(body.contains("\"email\":\"ada@example.com\""))
            assertTrue(body.contains("\"nickname\":\"Ada\""))
            assertTrue(body.contains("\"agreeTerms\":true"))
            assertTrue(body.contains("\"agreePrivacy\":true"))
            assertEquals("/api/auth/login", server.takeRequest().path)
            assertEquals("/api/characters?page=0&size=100", server.takeRequest().path)
            val patch = server.takeRequest()
            assertEquals("/api/me/character", patch.path)
            assertTrue(patch.body.readUtf8().contains("\"characterId\":5"))
            assertEquals("/api/me", server.takeRequest().path)
            assertEquals(1, registered)
            assertEquals(AvatarContent.Character(ProfileCharacter.MULBANGUL), account.profile.avatar)
        }
    }

    @Test fun retrySkipsRegisterAndCharacterFailureIsBestEffort() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(tokensJson))
            server.enqueue(MockResponse().setResponseCode(500).setBody("bad"))
            server.enqueue(MockResponse().setBody(me()))
            var registered = 0
            val account = fixture(server).signUp("ada@example.com", "password1", "Ada",
                AvatarContent.Character(ProfileCharacter.MONGSIL), true, true, true, { registered++ }) { error("unused") }
            assertEquals("/api/auth/login", server.takeRequest().path)
            assertEquals("/api/characters?page=0&size=100", server.takeRequest().path)
            assertEquals("/api/me", server.takeRequest().path)
            assertEquals(0, registered)
            assertEquals("Ada", account.profile.nickname)
        }
    }

    @Test fun failedCharacterPatchStillReturnsAccount() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(tokensJson))
            server.enqueue(MockResponse().setBody("""{"content":[{"id":1,"name":"Mongsil","svgKey":"mongsil"}]}"""))
            server.enqueue(MockResponse().setResponseCode(500).setBody("bad"))
            server.enqueue(MockResponse().setBody(me()))
            val account = fixture(server).signUp("ada@example.com", "password1", "Ada",
                AvatarContent.Character(ProfileCharacter.MONGSIL), true, true, true, {}) { error("unused") }
            assertEquals("Ada", account.profile.nickname)
            assertEquals(4, server.requestCount)
        }
    }

    @Test fun registerConflictAndLoginFailurePropagate() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(409).setBody("""{"code":"EMAIL_TAKEN","message":"taken"}"""))
            val tokens = InMemoryTokenStore()
            val repo = fixture(server, tokens)
            assertEquals("EMAIL_TAKEN", assertThrows(ApiException::class.java) { runBlocking {
                repo.signUp("a@b.com", "password1", "Ada",
                    AvatarContent.Character(ProfileCharacter.MONGSIL), true, true, false, {}) { error("unused") }
            } }.code)
            assertNull(tokens.accessToken)
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"INVALID_CREDENTIALS","message":"wrong"}"""))
            assertEquals("INVALID_CREDENTIALS", assertThrows(ApiException::class.java) { runBlocking {
                repo.login("a@b.com", "wrong")
            } }.code)
        }
    }

    @Test fun restoreInvalidTokenClearsButNetworkFailureKeepsTokens() = runBlocking {
        MockWebServer().use { server ->
            val tokens = InMemoryTokenStore().apply { save("a", "r") }
            val repo = fixture(server, tokens)
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"INVALID_TOKEN","message":"invalid"}"""))
            assertThrows(ApiException::class.java) { runBlocking { repo.restore() } }
            assertNull(tokens.refreshToken)
        }
        val server = MockWebServer()
        server.start()
        val url = server.url("/").toString()
        server.shutdown()
        val tokens = InMemoryTokenStore().apply { save("a", "r") }
        val repo = AuthRepository(ApiClient(url, tokens), tokens)
        assertThrows(NetworkException::class.java) { runBlocking { repo.restore() } }
        assertEquals("r", tokens.refreshToken)
    }
}
