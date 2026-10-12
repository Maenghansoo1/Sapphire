package com.sapphire.hawe.core.net

import com.sapphire.hawe.core.data.SubmissionContent
import com.sapphire.hawe.core.model.MissionType
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class SubmissionRepositoryTest {
    private fun response(id: Long = 100L, template: String = "PHOTO_ONE", media: String? = "/files/a.jpg",
        late: Boolean = false): String = """{"id":$id,"missionId":10,"author":{"userId":3,"nickname":"서연","characterKey":"mulbangul"},"template":"$template","mediaUrl":${media?.let { "\"$it\"" } ?: "null"},"submittedAt":"2026-09-28T21:00:00+09:00","isLate":$late,"duration":9.4,"waveform":[0.25,0.42],"text":"답","reactions":[],"commentCount":0}"""

    @Test fun imageAndTextSendOnlyAllowedParts() = runBlocking {
        MockWebServer().use { server ->
            repeat(3) { server.enqueue(MockResponse().setResponseCode(201).setBody(response())) }
            val repo = SubmissionRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            repo.submit(10L, SubmissionContent.Image(byteArrayOf(1, 2, 3), "jpg"), 3L)
            repo.submit(10L, SubmissionContent.Image(byteArrayOf(4, 5), "png"), 3L)
            repo.submit(10L, SubmissionContent.Text("안녕"), 3L)
            val photo = server.takeRequest()
            assertTrue(photo.getHeader("Content-Type")!!.startsWith("multipart/form-data; boundary="))
            assertTrue(photo.body.readUtf8().let { "name=\"file\"; filename=\"photo.jpg\"" in it &&
                "image/jpeg" in it && "name=\"text\"" !in it })
            val drawing = server.takeRequest().body.readUtf8()
            assertTrue("name=\"file\"; filename=\"drawing.png\"" in drawing)
            assertTrue("image/png" in drawing)
            val text = server.takeRequest().body.readUtf8()
            assertTrue("name=\"text\"" in text)
            assertFalse("name=\"file\"" in text)
            assertFalse("name=\"caption\"" in text)
        }
    }

    @Test fun voiceParametersAndResponseMapping() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(201).setBody(response(template = "VOICE_LINE",
                media = "/files/a.m4a", late = true)))
            val tokens = InMemoryTokenStore().apply { save("access", "refresh") }
            val repo = SubmissionRepository(ApiClient(server.url("/").toString(), tokens))
            val file = File.createTempFile("hawe-voice", ".m4a")
            try {
                file.writeBytes(byteArrayOf(1, 2, 3))
                val submission = repo.submit(10L, SubmissionContent.Voice(file, 30.4,
                    (0..69).map { if (it == 0) Float.NaN else it / 60f }), 3L)
                assertEquals(100L, submission.id)
                assertEquals(MissionType.VOICE, submission.type)
                assertTrue(submission.isMine)
                assertTrue(submission.isLate)
                assertEquals("서연", submission.author.nickname)
                assertEquals(server.url("/files/a.m4a").toString(), submission.mediaURL.toString())
                assertEquals(9.4, submission.duration!!, 0.001)
                assertEquals(2, submission.waveform.size)
                val request = server.takeRequest()
                assertEquals("Bearer access", request.getHeader("Authorization"))
                val body = request.body.readUtf8()
                assertTrue("audio/mp4" in body)
                assertTrue("name=\"duration\"" in body && "30.00" in body)
                assertTrue("name=\"waveform\"" in body)
                assertFalse("NaN" in body)
            } finally { file.delete() }
        }
        assertEquals("0.10", voiceDurationParam(0.0))
        assertEquals(64, waveformParam((0..69).map { it / 60f }).removeSurrounding("[", "]").split(",").size)
        assertEquals("[0.0,1.0]", waveformParam(listOf(Float.NaN, 2f)))
    }

    @Test fun feedKeepsOrderSkipsUnknownAndPropagatesLock() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"content":[${response(1L)},${response(2L, "UNEXPECTED")},${response(3L, "TEXT_ACROSTIC", null)}],"page":0,"size":100,"totalPages":1,"totalElements":3}"""))
            server.enqueue(MockResponse().setResponseCode(423).setBody("""{"code":"FEED_LOCKED","message":"locked"}"""))
            val repo = SubmissionRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            val feed = repo.feed(10L, 4L)
            assertEquals(listOf(1L, 3L), feed.map { it.id })
            assertFalse(feed.first().isMine)
            assertEquals(MissionType.WRITE, feed.last().type)
            assertEquals("답", feed.last().text)
            assertEquals("/api/missions/10/submissions?page=0&size=100", server.takeRequest().path)
            val error = assertThrows(ApiException::class.java) { runBlocking { repo.feed(10L, 4L) } }
            assertEquals(423, error.status)
            assertEquals("FEED_LOCKED", error.code)
        }
    }

    @Test fun failuresPreserveServerCodesAndMessages() = runBlocking {
        for ((status, code) in listOf(409 to "ALREADY_SUBMITTED", 409 to "MISSION_CLOSED",
            400 to "INVALID_SUBMISSION", 413 to "FILE_TOO_LARGE", 415 to "UNSUPPORTED_FILE_TYPE")) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(status).setBody("""{"code":"$code","message":"server reason"}"""))
                val repo = SubmissionRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
                val error = assertThrows(ApiException::class.java) { runBlocking {
                    repo.submit(10L, SubmissionContent.Text("답"), 3L)
                } }
                assertEquals(status, error.status)
                assertEquals(code, error.code)
                assertEquals("server reason", error.message)
            }
        }
    }

    @Test fun failureClassification() {
        assertEquals(SubmitOutcome.AlreadySubmitted, classifySubmitFailure(ApiException(409, "ALREADY_SUBMITTED", "")))
        assertEquals(SubmitOutcome.MissionClosed, classifySubmitFailure(ApiException(409, "MISSION_CLOSED", "")))
        assertEquals(SubmitOutcome.ReturnHome, classifySubmitFailure(ApiException(403, "NOT_GROUP_MEMBER", "")))
        assertEquals(SubmitOutcome.ReturnHome, classifySubmitFailure(ApiException(404, "MISSION_NOT_FOUND", "")))
        assertEquals(SubmitOutcome.Stay("wrong"), classifySubmitFailure(ApiException(400, "INVALID_SUBMISSION", "wrong")))
        assertEquals(SubmitOutcome.Stay("파일이 너무 커요"), classifySubmitFailure(ApiException(413, "FILE_TOO_LARGE", "")))
        assertEquals(SubmitOutcome.Stay("지원하지 않는 파일이에요"), classifySubmitFailure(ApiException(415, "UNSUPPORTED_FILE_TYPE", "")))
        assertEquals(SubmitOutcome.Stay("네트워크 연결을 확인해 주세요"),
            classifySubmitFailure(NetworkException(java.io.IOException("offline"))))
    }
}
