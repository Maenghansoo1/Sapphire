package com.sapphire.hawe.core.net

import com.sapphire.hawe.core.model.Sticker
import com.sapphire.hawe.core.model.AiDetail
import com.sapphire.hawe.feature.results.mentionedIdsIn
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class SubmissionSocialRepositoryTest {
    private val comment = """{"id":2,"author":{"userId":3,"nickname":"잎사귀","characterKey":"ipsagwi"},"content":"@몽실이 뭐야","imageUrl":"/files/comments/a.jpg","mentionedUserIds":[1],"createdAt":"2026-09-28T21:10:00+09:00","mine":true}"""
    private fun repo(server: MockWebServer) =
        SubmissionRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore().apply { save("a", "r") }))

    @Test fun toggleReactionSendsKeyAndMapsReactions() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"reactions":[{"stickerType":"heart","count":2,"reactedByMe":true,"userIds":[1,3]},{"stickerType":"unknown","count":1,"reactedByMe":false,"userIds":[5]}]}"""))
            val reactions = repo(server).toggleReaction(7L, Sticker.HEART)
            val request = server.takeRequest()
            assertEquals("/api/submissions/7/reactions", request.path)
            assertEquals("""{"stickerType":"heart"}""", request.body.readUtf8())
            assertEquals(1, reactions.size)
            assertEquals(Sticker.HEART, reactions[0].sticker)
            assertTrue(reactions[0].reactedByMe)
            assertEquals(listOf(1L, 3L), reactions[0].userIds)
        }
    }

    @Test fun commentsMapAuthorImageAndMine() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"content":[$comment],"page":0,"size":100,"totalPages":1,"totalElements":1}"""))
            val list = repo(server).comments(7L)
            assertEquals("/api/submissions/7/comments?page=0&size=100", server.takeRequest().path)
            assertEquals("잎사귀", list[0].author.nickname)
            assertTrue(list[0].isMine)
            assertEquals(listOf(1L), list[0].mentionedUserIds)
            assertEquals(server.url("/files/comments/a.jpg").toString(), list[0].imageURL.toString())
        }
    }

    @Test fun mascotCommentAndReactionMapWithoutAddingUserId() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"content":[{"id":9,"author":{"userId":7,"nickname":"별이"},"content":"고양이인가요?","createdAt":"2026-09-28T21:10:00+09:00","ai":true,"aiDetail":{"kind":"DRAW_GUESS","guesses":[{"label":"고양이","probability":0.62}]}}],"page":0,"size":100,"totalPages":1,"totalElements":1}"""))
            server.enqueue(MockResponse().setBody("""{"reactions":[{"stickerType":"wow","count":1,"userIds":[],"aiReacted":true}]}"""))
            val repo = repo(server)
            val mascot = repo.comments(7L).single()
            assertTrue(mascot.isAi)
            assertEquals(AiDetail.DrawGuess(listOf(com.sapphire.hawe.core.model.AiGuessEntry("고양이", 0.62f))), mascot.aiDetail)
            val reaction = repo.toggleReaction(7L, Sticker.WOW).single()
            assertEquals(1, reaction.count)
            assertTrue(reaction.aiReacted)
            assertTrue(reaction.userIds.isEmpty())
        }
    }

    @Test fun addCommentSendsRepeatedMentionsAndOptionalImage() = runBlocking {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setResponseCode(201).setBody(comment)) }
            val repo = repo(server)
            repo.addComment(7L, "안녕", listOf(1L, 1L, 4L), byteArrayOf(1, 2, 3), "png")
            val withImage = server.takeRequest().body.readUtf8()
            assertEquals(2, Regex("name=\"mentionedUserIds\"").findAll(withImage).count())
            assertTrue("name=\"file\"; filename=\"comment.png\"" in withImage)
            assertTrue("name=\"content\"" in withImage)
            repo.addComment(7L, "", emptyList(), null)
            val textOnly = server.takeRequest().body.readUtf8()
            assertFalse("name=\"file\"" in textOnly)
            assertFalse("name=\"mentionedUserIds\"" in textOnly)
        }
    }

    @Test fun deleteCommentUsesDelete() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(204))
            repo(server).deleteComment(2L)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/api/comments/2", request.path)
        }
    }

    @Test fun mentionsKeepOnlyNamesStillInText() {
        val picked = mapOf(1L to "몽실이", 4L to "잎사귀")
        assertEquals(listOf(1L), mentionedIdsIn("@몽실이 안녕", picked))
        assertEquals(emptyList<Long>(), mentionedIdsIn("안녕", picked))
    }
}
