package com.sapphire.hawe.core.net

import com.sapphire.hawe.core.model.Profile
import com.sapphire.hawe.designsystem.assets.ProfileCharacter
import com.sapphire.hawe.designsystem.assets.Relationship
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test

class GroupRepositoryTest {
    @Test fun coupleCreationSendsStartDate() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(response(detail.replace("\"FRIEND\"", "\"COUPLE\"")
                .replace("\"startDate\":null", "\"startDate\":\"2026-01-01\"")
                .replace("\"ownerId\":5", "\"ownerId\":8")
                .replace("\"memberCount\":2", "\"memberCount\":1")))
            val groups = GroupRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            val created = groups.create("둘", Relationship.LOVER, LocalDate.of(2026, 1, 1), 8L, Profile.mockMine)
            assertEquals(Relationship.LOVER, created.relationship)
            assertEquals(LocalDate.of(2026, 1, 1), created.startDate)
            assertEquals(listOf(8L), created.members.map { it.id })
            assertTrue(created.amIOwner)
            assertEquals(Profile.mockMine, created.me!!.defaultProfile)
            assertTrue(server.takeRequest().body.readUtf8().contains("\"startDate\":\"2026-01-01\""))
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun groupErrorsArePreserved() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404).setBody("""{"code":"INVITE_NOT_FOUND","message":"missing"}"""))
            server.enqueue(MockResponse().setResponseCode(409).setBody("""{"code":"SUCCESSOR_REQUIRED","message":"successor"}"""))
            val groups = GroupRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            val preview = assertThrows(ApiException::class.java) { runBlocking { groups.preview("BAD123") } }
            assertEquals("INVITE_NOT_FOUND", preview.code)
            val leave = assertThrows(ApiException::class.java) { runBlocking { groups.leave(7L) } }
            assertEquals("SUCCESSOR_REQUIRED", leave.code)
        }
    }
    private val detail = """{"id":7,"name":"친구들","ownerId":5,"relationType":"FRIEND","createdAt":"2026-10-12T09:00:00+09:00","memberCount":2,"maxMembers":6,"inviteCode":"ABC123","inviteUrl":"url","startDate":null,"dailyMissionCount":1,"missionSlots":[{"slotNo":1,"publishTime":"10:30","deadlineTime":"23:00","custom":true}],"missionCount":3}"""
    private val members = """[{"userId":5,"nickname":"owner","characterKey":"gamja","role":"owner"},{"userId":8,"nickname":"me","characterKey":"missing","role":"member"}]"""
    private fun response(body: String) = MockResponse().setBody(body)

    @Test fun loadAllCombinesListAndDetailsAndSkipsMissing() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                    "/api/groups?page=0&size=100" -> response("""{"content":[{"id":7,"name":"친구들","relationType":"FRIEND","memberCount":2,"maxMembers":6,"members":$members,"todayMission":{"missionId":10,"media":"TEXT","content":"서버 주제","submittedCount":1,"submittedByMe":false}},{"id":9,"name":"gone","relationType":"CASUAL","memberCount":0,"maxMembers":6,"members":[]}]}""")
                    "/api/groups/7" -> response(detail)
                    "/api/groups/9" -> MockResponse().setResponseCode(404).setBody("""{"code":"GROUP_NOT_FOUND","message":"gone"}""")
                    else -> MockResponse().setResponseCode(500)
                }
            }
            val groups = GroupRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            val loaded = groups.loadAll(8L).single()
            val result = loaded.detail
            assertEquals(10L, loaded.mission?.missionId)
            assertEquals(com.sapphire.hawe.core.model.MissionType.WRITE, loaded.mission?.type)
            assertEquals("서버 주제", loaded.mission?.topic)
            assertEquals(7L, result.id)
            assertEquals(LocalTime.of(10, 30), result.topicTime)
            assertEquals(ProfileCharacter.GAMJA, (result.members.first().displayed.avatar as com.sapphire.hawe.core.model.AvatarContent.Character).character)
            assertEquals(ProfileCharacter.DEFAULT, (result.members.last().displayed.avatar as com.sapphire.hawe.core.model.AvatarContent.Character).character)
            assertTrue(result.members.last().isMe)
            assertTrue(result.members.first().isOwner)
        }
    }

    @Test fun detailCreatePreviewJoinAndLeave() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path == "/api/groups/7" -> response(detail)
                    request.path == "/api/groups/7/members?page=0&size=100" -> response("""{"content":$members}""")
                    request.path == "/api/groups" -> response(detail.replace("\"ownerId\":5", "\"ownerId\":8")
                        .replace("\"memberCount\":2", "\"memberCount\":1"))
                        .setResponseCode(201)
                    request.path == "/api/invites/ABC123" -> response("""{"groupId":7,"name":"친구들","relationType":"FRIEND","memberCount":2,"maxMembers":6,"members":$members}""")
                    request.path == "/api/groups/join" -> response("""{"groupId":7,"name":"친구들"}""")
                    request.path!!.startsWith("/api/groups/7/members/me") -> MockResponse().setResponseCode(204)
                    else -> MockResponse().setResponseCode(500)
                }
            }
            val groups = GroupRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            assertEquals(2, groups.detail(7L, 8L).members.size)
            val created = groups.create(" 친구들 ", Relationship.FRIEND, LocalDate.now(), 8L, Profile.mockMine)
            assertEquals(7L, created.id)
            assertEquals(1, created.members.size)
            assertTrue(created.amIOwner)
            assertEquals(3, server.requestCount)
            assertEquals(2, groups.preview("ABC123").memberCount)
            assertEquals(7L, groups.join("ABC123"))
            groups.leave(7L, 8L)
            val requests = (1..6).map { server.takeRequest() }
            val create = requests.first { it.path == "/api/groups" }
            assertTrue(create.body.readUtf8().contains("\"name\":\"친구들\""))
            assertEquals("DELETE", requests.last().method)
            assertEquals("/api/groups/7/members/me?successorUserId=8", requests.last().path)
        }
    }
}
