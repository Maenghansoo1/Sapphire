package com.sapphire.hawe.core.net

import com.sapphire.hawe.core.data.SubmissionContent
import com.sapphire.hawe.core.model.ConsensusRank
import com.sapphire.hawe.core.model.MissionAggregate
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.SliderPoint
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class SubmissionRepositoryTest {
    private fun response(id: Long = 100L, template: String = "PHOTO_ONE", media: String? = "/files/a.jpg",
        late: Boolean = false): String = """{"id":$id,"missionId":10,"author":{"userId":3,"nickname":"서연","characterKey":"mulbangul"},"template":"$template","mediaUrl":${media?.let { "\"$it\"" } ?: "null"},"submittedAt":"2026-09-28T21:00:00+09:00","isLate":$late,"text":"답","reactions":[],"commentCount":0}"""

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

    @Test fun submitMapsResponse() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(201).setBody(response(template = "DRAW_SKETCH",
                media = "/files/a.png", late = true)))
            val tokens = InMemoryTokenStore().apply { save("access", "refresh") }
            val repo = SubmissionRepository(ApiClient(server.url("/").toString(), tokens))
            val submission = repo.submit(10L, SubmissionContent.Image(byteArrayOf(1, 2, 3), "png"), 3L)
            assertEquals(100L, submission.id)
            assertEquals(MissionType.DRAW, submission.type)
            assertTrue(submission.isMine)
            assertTrue(submission.isLate)
            assertEquals("서연", submission.author.nickname)
            assertEquals(server.url("/files/a.png").toString(), submission.mediaURL.toString())
            assertEquals("Bearer access", server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test fun feedKeepsOrderSkipsUnknownAndPropagatesLock() = runBlocking {
        MockWebServer().use { server ->
            // 예전 음성 제출(VOICE_LINE)·모르는 틀은 건너뛴다
            server.enqueue(MockResponse().setBody("""{"content":[${response(1L)},${response(2L, "UNEXPECTED")},${response(4L, "VOICE_LINE", "/files/a.m4a")},${response(3L, "TEXT_ACROSTIC", null)}],"page":0,"size":100,"totalPages":1,"totalElements":4}"""))
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

    /** multipart 본문의 텍스트 필드(name → 값). 파일 파트는 테스트하지 않아요. */
    private fun formFields(body: String): Map<String, String> =
        Regex("""name="(\w+)"\r\n(?:[^\r\n]+\r\n)*\r\n(.*?)\r\n--""", RegexOption.DOT_MATCHES_ALL)
            .findAll(body).associate { it.groupValues[1] to it.groupValues[2] }

    @Test fun scalarSubmissionsSendOnlyTemplateFields() = runBlocking {
        val cases = listOf(
            // 한 단어(이유 있음/없음), 후기, 밸런스(이유 없음), 슬라이더, 다수결 예측
            SubmissionContent.Scalar(text = "몰입", caption = "하루 종일 코딩") to
                mapOf("text" to "몰입", "caption" to "하루 종일 코딩"),
            SubmissionContent.Scalar(text = "피곤") to mapOf("text" to "피곤"),
            SubmissionContent.Scalar(number = 4, text = "또 먹고 싶다") to mapOf("number" to "4", "text" to "또 먹고 싶다"),
            SubmissionContent.Scalar(choice = "B") to mapOf("choice" to "B"),
            SubmissionContent.Scalar(number = 0) to mapOf("number" to "0"),
            SubmissionContent.Scalar(number = 80, guess = 60) to mapOf("number" to "80", "guess" to "60"),
        )
        MockWebServer().use { server ->
            repeat(cases.size) { server.enqueue(MockResponse().setResponseCode(201).setBody(response(template = "TEXT_REVIEW", media = null))) }
            val repo = SubmissionRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            cases.forEach { (content, _) -> repo.submit(10L, content, 3L) }
            cases.forEach { (_, expected) ->
                val request = server.takeRequest()
                assertEquals("/api/missions/10/submissions", request.path)
                val body = request.body.readUtf8()
                assertEquals(expected, formFields(body))
                assertFalse("name=\"file\"" in body)
            }
        }
    }

    @Test fun mapsNewTemplateFieldsAndSliderType() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":101,"missionId":12,"author":{"userId":3,"nickname":"서연"},"template":"SLIDER_CONSENSUS","number":80,"guess":60,"reactions":[],"commentCount":0,"submittedAt":"2026-10-13T21:00:00+09:00","isLate":false}"""))
            server.enqueue(MockResponse().setBody("""{"content":[
                {"id":1,"missionId":13,"author":{"userId":3,"nickname":"서연"},"template":"TEXT_BALANCE","choice":"A","caption":"면이 좋아","submittedAt":"2026-10-13T21:00:00+09:00"},
                {"id":2,"missionId":13,"author":{"userId":4,"nickname":"지우"},"template":"TEXT_REVIEW","text":"최고","number":5,"submittedAt":"2026-10-13T21:01:00+09:00"},
                {"id":3,"missionId":13,"author":{"userId":5,"nickname":"현우"},"template":"TEXT_ONE_WORD","text":"몰입","submittedAt":"2026-10-13T21:02:00+09:00"},
                {"id":4,"missionId":13,"author":{"userId":6,"nickname":"민준"},"template":"SLIDER_TASTE","number":0,"submittedAt":"2026-10-13T21:03:00+09:00"}],
                "page":0,"size":100,"totalPages":1,"totalElements":4}"""))
            val repo = SubmissionRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            val consensus = repo.submit(12L, SubmissionContent.Scalar(number = 80, guess = 60), 3L)
            assertEquals(MissionType.SLIDER, consensus.type)
            assertEquals("SLIDER_CONSENSUS", consensus.template)
            assertEquals(80, consensus.number)
            assertEquals(60, consensus.guess)
            assertNull(consensus.choice)
            assertNull(consensus.mediaURL)

            // 새 틀 제출이 피드에서 사라지면 안 된다(templateType 매핑)
            val feed = repo.feed(13L, 3L)
            assertEquals(listOf(1L, 2L, 3L, 4L), feed.map { it.id })
            assertEquals(listOf(MissionType.WRITE, MissionType.WRITE, MissionType.WRITE, MissionType.SLIDER), feed.map { it.type })
            assertEquals("A", feed[0].choice)
            assertEquals("면이 좋아", feed[0].caption)
            assertEquals(5, feed[1].number)
            assertEquals("최고", feed[1].text)
            assertEquals("몰입", feed[2].text)
            assertNull(feed[2].caption)
            assertEquals(0, feed[3].number)
        }
    }

    @Test fun resultMapsEachAggregateShape() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"template":"TEXT_BALANCE","totalCount":4,"choices":[{"choice":"A","count":3},{"choice":"B","count":1}],"myChoice":"A"}"""))
            server.enqueue(MockResponse().setBody("""{"template":"SLIDER_POSITION","totalCount":2,"average":45.0,"distribution":[{"userId":1,"nickname":"몽실이","number":70},{"userId":2,"nickname":"잎사귀","number":20}],"myNumber":70,"diffFromAverage":25.0}"""))
            server.enqueue(MockResponse().setBody("""{"template":"SLIDER_CONSENSUS","totalCount":3,"average":62.5,"ranking":[{"userId":2,"nickname":"잎사귀","guess":60,"diff":2.5},{"userId":3,"nickname":"서연","guess":65,"diff":2.5},{"userId":1,"nickname":"몽실이","guess":80,"diff":17.5}],"winnerUserIds":[2,3]}"""))
            server.enqueue(MockResponse().setBody("""{"template":"SLIDER_FUTURE","totalCount":1}"""))
            val repo = SubmissionRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))

            assertEquals(MissionAggregate.Balance(4, 3, 1, "A"), repo.result(30L))
            assertEquals("/api/missions/30/result", server.takeRequest().path)
            assertEquals(MissionAggregate.Slider(2, 45.0,
                listOf(SliderPoint(1L, "몽실이", 70), SliderPoint(2L, "잎사귀", 20)), 70, 25.0), repo.result(31L))
            assertEquals(MissionAggregate.Consensus(3, 62.5, listOf(ConsensusRank(2L, "잎사귀", 60, 2.5),
                ConsensusRank(3L, "서연", 65, 2.5), ConsensusRank(1L, "몽실이", 80, 17.5)), setOf(2L, 3L)), repo.result(32L))
            assertNull(repo.result(33L))
        }
    }

    @Test fun resultPropagatesLockAndUnsupported() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(423).setBody("""{"code":"FEED_LOCKED","message":"locked"}"""))
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"RESULT_NOT_SUPPORTED","message":"no"}"""))
            val repo = SubmissionRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            val locked = assertThrows(ApiException::class.java) { runBlocking { repo.result(30L) } }
            assertEquals("FEED_LOCKED", locked.code)
            val unsupported = assertThrows(ApiException::class.java) { runBlocking { repo.result(30L) } }
            assertEquals(400, unsupported.status)
            assertEquals("RESULT_NOT_SUPPORTED", unsupported.code)
        }
    }
}
