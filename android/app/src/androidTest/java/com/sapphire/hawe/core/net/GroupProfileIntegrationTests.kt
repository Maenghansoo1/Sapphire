package com.sapphire.hawe.core.net

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sapphire.hawe.core.model.AvatarContent
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupProfileIntegrationTests {
    @Test fun newPhotoUploadsFileAndNickname() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"userId":8,"nickname":"별명","profileImageUrl":"/files/new.jpg"}"""))
            val groups = GroupRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            groups.saveGroupProfile(7L, AvatarContent.Photo(Uri.parse("file:///tmp/photo.jpg")), " 별명 ") {
                byteArrayOf(1, 2, 3)
            }
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/groups/7/members/me/profile-image", request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("name=\"nickname\""))
            assertTrue(body.contains("별명"))
            assertTrue(body.contains("name=\"file\"; filename=\"profile.jpg\""))
        }
    }

    @Test fun existingRemotePhotoDoesNotUploadAgain() = runBlocking {
        MockWebServer().use { server ->
            val groups = GroupRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            groups.saveGroupProfile(7L, AvatarContent.Photo(Uri.parse("https://example.com/photo.jpg")),
                "별명") { error("unused") }
            assertEquals(0, server.requestCount)
        }
    }
}
