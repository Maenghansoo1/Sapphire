package com.sapphire.hawe.core.net

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sapphire.hawe.core.model.AvatarContent
import com.sapphire.hawe.core.model.Profile
import com.sapphire.hawe.designsystem.assets.ProfileCharacter
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfilePhotoIntegrationTests {
    private val tokens = """{"accessToken":"access","refreshToken":"refresh"}"""
    private fun me(photo: String? = null, character: String = "mongsil") =
        """{"id":1,"email":"ada@example.com","nickname":"Ada","characterKey":"$character","profileImageUrl":${photo?.let { "\"$it\"" } ?: "null"},"createdAt":"2026-09-28T09:00:00+09:00"}"""
    private fun fixture(server: MockWebServer): AuthRepository {
        val store = InMemoryTokenStore()
        return AuthRepository(ApiClient(server.url("/").toString(), store), store)
    }
    private fun photo(value: String) = AvatarContent.Photo(Uri.parse(value))
    private fun photoUrl(avatar: AvatarContent) = (avatar as AvatarContent.Photo).uri.toString()

    @Test fun profilePhotoUploadUsesPutAndRestoresServerPhoto() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"profileImageUrl":"/files/new.jpg"}"""))
            server.enqueue(MockResponse().setBody(me("/files/new.jpg")))
            val current = Profile(AvatarContent.Character(ProfileCharacter.MONGSIL), "Ada")
            val result = fixture(server).saveProfile(current, "Ada", photo("file:///tmp/avatar.jpg")) {
                byteArrayOf(1, 2, 3)
            }
            assertNull(result.error)
            assertEquals(server.url("/files/new.jpg").toString(), photoUrl(result.account!!.profile.avatar))
            val upload = server.takeRequest()
            assertEquals("PUT", upload.method)
            assertEquals("/api/me/profile-image", upload.path)
            assertTrue(upload.body.readUtf8().contains("name=\"file\"; filename=\"profile.jpg\""))
            assertEquals("/api/me", server.takeRequest().path)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun changingPhotoToCharacterPatchesThenDeletesPhoto() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"content":[{"id":5,"name":"water","svgKey":"mulbangul"}]}"""))
            server.enqueue(MockResponse().setBody("""{"characterId":5,"name":"water","svgKey":"mulbangul"}"""))
            server.enqueue(MockResponse().setResponseCode(204))
            server.enqueue(MockResponse().setBody(me(character = "mulbangul")))
            val result = fixture(server).saveProfile(Profile(photo("https://example.com/old.jpg"), "Ada"),
                "Ada", AvatarContent.Character(ProfileCharacter.MULBANGUL)) { error("unused") }
            assertNull(result.error)
            assertEquals("/api/characters?page=0&size=100", server.takeRequest().path)
            val patch = server.takeRequest()
            assertEquals("PATCH", patch.method)
            assertEquals("/api/me/character", patch.path)
            assertTrue(patch.body.readUtf8().contains("\"characterId\":5"))
            val delete = server.takeRequest()
            assertEquals("DELETE", delete.method)
            assertEquals("/api/me/profile-image", delete.path)
            assertEquals("/api/me", server.takeRequest().path)
        }
    }

    @Test fun unchangedRemotePhotoOnlyRestores() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(me("/files/existing.jpg")))
            val avatar = photo("https://example.com/existing.jpg")
            val result = fixture(server).saveProfile(Profile(avatar, "Ada"), "Ada", avatar) { error("unused") }
            assertNull(result.error)
            assertEquals("/api/me", server.takeRequest().path)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun photoFailureReturnsErrorAndRestoredNickname() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(me()))
            server.enqueue(MockResponse().setResponseCode(413)
                .setBody("""{"code":"FILE_TOO_LARGE","message":"large"}"""))
            server.enqueue(MockResponse().setBody(me()))
            val current = Profile(AvatarContent.Character(ProfileCharacter.MONGSIL), "Old")
            val result = fixture(server).saveProfile(current, "Ada", photo("file:///tmp/avatar.jpg")) {
                byteArrayOf(1, 2, 3)
            }
            assertEquals("FILE_TOO_LARGE", (result.error as ApiException).code)
            assertEquals("Ada", result.account!!.profile.nickname)
            assertEquals("/api/me/profile", server.takeRequest().path)
            assertEquals("/api/me/profile-image", server.takeRequest().path)
            assertEquals("/api/me", server.takeRequest().path)
        }
    }

    @Test fun signupUploadsPhotoAndKeepsAccountWhenUploadFails() = runBlocking {
        listOf(false, true).forEach { fails ->
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(201)
                    .setBody("""{"id":1,"email":"ada@example.com","nickname":"Ada"}"""))
                server.enqueue(MockResponse().setBody(tokens))
                server.enqueue(if (fails) MockResponse().setResponseCode(413)
                    .setBody("""{"code":"FILE_TOO_LARGE","message":"large"}""")
                    else MockResponse().setBody("""{"profileImageUrl":"/files/new.jpg"}"""))
                server.enqueue(MockResponse().setBody(me(if (fails) null else "/files/new.jpg")))
                var registered = false
                val account = fixture(server).signUp("ada@example.com", "password1", "Ada",
                    photo("file:///tmp/avatar.jpg"), true, true, false, { registered = true }) {
                    byteArrayOf(1, 2, 3)
                }
                assertTrue(registered)
                assertEquals("/api/auth/register", server.takeRequest().path)
                assertEquals("/api/auth/login", server.takeRequest().path)
                val upload = server.takeRequest()
                assertEquals("PUT", upload.method)
                assertEquals("/api/me/profile-image", upload.path)
                assertEquals("/api/me", server.takeRequest().path)
                if (fails) assertTrue(account.profile.avatar is AvatarContent.Character)
                else assertEquals(server.url("/files/new.jpg").toString(), photoUrl(account.profile.avatar))
            }
        }
    }

    @Test fun accountAndGroupPhotoUrlsOverrideCharacters() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(me("/files/me.jpg")))
            val account = fixture(server).restore()
            assertEquals(server.url("/files/me.jpg").toString(), photoUrl(account.profile.avatar))
            assertEquals("/api/me", server.takeRequest().path)
            server.enqueue(MockResponse().setBody(me()))
            assertTrue(fixture(server).restore().profile.avatar is AvatarContent.Character)
            server.takeRequest()

            val member = """{"userId":1,"nickname":"Ada","characterKey":"mongsil","profileImageUrl":"/files/member.jpg","role":"owner"}"""
            val detail = """{"id":7,"name":"Friends","ownerId":1,"relationType":"FRIEND","createdAt":"2026-10-12T09:00:00+09:00","memberCount":1,"maxMembers":6,"inviteCode":"ABC123","inviteUrl":"url","dailyMissionCount":1,"missionSlots":[{"slotNo":1,"publishTime":"10:30","deadlineTime":"23:00","custom":true}],"missionCount":3}"""
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                    "/api/groups?page=0&size=100" -> MockResponse().setBody("""{"content":[{"id":7,"name":"Friends","relationType":"FRIEND","memberCount":1,"maxMembers":6,"members":[$member]}]}""")
                    "/api/groups/7" -> MockResponse().setBody(detail)
                    "/api/groups/7/members?page=0&size=100" -> MockResponse().setBody("""{"content":[$member]}""")
                    "/api/invites/ABC123" -> MockResponse().setBody("""{"groupId":7,"name":"Friends","relationType":"FRIEND","memberCount":1,"maxMembers":6,"members":[$member]}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
            val groups = GroupRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            val group = groups.detail(7L, 1L)
            assertEquals(server.url("/files/member.jpg").toString(), photoUrl(group.members.single().defaultProfile.avatar))
            assertEquals(setOf("/api/groups/7", "/api/groups/7/members?page=0&size=100"),
                setOf(server.takeRequest().path, server.takeRequest().path))
            assertEquals(server.url("/files/member.jpg").toString(),
                photoUrl(groups.loadAll(1L).single().detail.members.single().defaultProfile.avatar))
            assertEquals(setOf("/api/groups?page=0&size=100", "/api/groups/7"),
                setOf(server.takeRequest().path, server.takeRequest().path))
            assertEquals(server.url("/files/member.jpg").toString(), photoUrl(groups.preview("ABC123").members.single()))
            assertEquals("/api/invites/ABC123", server.takeRequest().path)
        }
    }

    @Test fun submissionAndCommentAuthorsUsePhotoUrls() = runBlocking {
        MockWebServer().use { server ->
            val author = """{"userId":3,"nickname":"Ada","characterKey":"mongsil","profileImageUrl":"/files/author.jpg"}"""
            server.enqueue(MockResponse().setBody("""{"content":[{"id":11,"missionId":10,"author":$author,"template":"TEXT_ONE_WORD","text":"hello","submittedAt":"2026-10-13T21:00:00+09:00"}],"page":0,"size":100,"totalPages":1,"totalElements":1}"""))
            server.enqueue(MockResponse().setBody("""{"content":[{"id":12,"author":$author,"content":"hello","createdAt":"2026-10-13T21:00:00+09:00"}],"page":0,"size":100,"totalPages":1,"totalElements":1}"""))
            val submissions = SubmissionRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            assertEquals(server.url("/files/author.jpg").toString(),
                photoUrl(submissions.feed(10L, 3L).single().author.avatar))
            assertEquals(server.url("/files/author.jpg").toString(),
                photoUrl(submissions.comments(11L).single().author.avatar))
            assertEquals("/api/missions/10/submissions?page=0&size=100", server.takeRequest().path)
            assertEquals("/api/submissions/11/comments?page=0&size=100", server.takeRequest().path)
        }
    }
}
