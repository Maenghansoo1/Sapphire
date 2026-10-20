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
    @Test fun groupProfileFlagSeparatesDisplayedOverride() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                    "/api/groups/7" -> response(detail)
                    "/api/groups/7/members?page=0&size=100" -> response("""{"content":[
                        {"userId":5,"nickname":"owner","characterKey":"gamja","role":"owner","hasGroupProfile":true,"submissionCountThisMonth":4,"stampedToday":true},
                        {"userId":8,"nickname":"me","characterKey":"mongsil","role":"member","hasGroupProfile":false}]}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
            val group = GroupRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore())).detail(7L, 8L)
            assertEquals(group.members.first().defaultProfile, group.members.first().groupProfile)
            assertNull(group.members.last().groupProfile)
            assertEquals(4, group.members.first().monthlySubmissionCount)
            assertEquals(true, group.members.first().stampedToday)
            assertNull(group.members.last().monthlySubmissionCount)
            assertNull(group.members.last().stampedToday)
        }
    }

    @Test fun groupProfileAndInviteRequestsUseServerContracts() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(response("""{"content":[{"id":5,"name":"water","svgKey":"mulbangul"}]}"""))
            server.enqueue(response("""{"userId":8,"nickname":"별명","characterKey":"mulbangul"}"""))
            server.enqueue(MockResponse().setResponseCode(204))
            server.enqueue(response("""{"inviteCode":"NEW123","inviteUrl":"https://example.com/invite"}"""))
            val groups = GroupRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            groups.saveGroupProfile(7L, com.sapphire.hawe.core.model.AvatarContent.Character(ProfileCharacter.MULBANGUL),
                " 별명 ") { error("unused") }
            groups.clearGroupProfile(7L)
            assertEquals("NEW123", groups.reissueInviteCode(7L).inviteCode)
            assertEquals("/api/characters?page=0&size=100", server.takeRequest().path)
            val save = server.takeRequest()
            assertEquals("PUT", save.method)
            assertEquals("/api/groups/7/members/me/profile", save.path)
            val body = save.body.readUtf8()
            assertTrue(body.contains("\"characterId\":5"))
            assertTrue(body.contains("\"nickname\":\"별명\""))
            val clear = server.takeRequest()
            assertEquals("DELETE", clear.method)
            assertEquals("/api/groups/7/members/me/profile", clear.path)
            val reissue = server.takeRequest()
            assertEquals("POST", reissue.method)
            assertEquals("/api/groups/7/invite-code", reissue.path)
        }
    }

    @Test fun topicTimeUpdatePreservesOtherCustomSlots() = runBlocking {
        MockWebServer().use { server ->
            val slots = """[{"slotNo":1,"publishTime":"09:00","deadlineTime":"12:00","custom":true},
                {"slotNo":2,"publishTime":"13:00","deadlineTime":"18:00","custom":true},
                {"slotNo":3,"publishTime":"19:00","deadlineTime":"23:00","custom":false}]"""
            val withSlots = detail.replace("\"missionSlots\":[{\"slotNo\":1,\"publishTime\":\"10:30\",\"deadlineTime\":\"23:00\",\"custom\":true}]",
                "\"missionSlots\":$slots")
            server.enqueue(response(withSlots))
            server.enqueue(response(withSlots.replace("\"publishTime\":\"09:00\"", "\"publishTime\":\"00:05\"")))
            val groups = GroupRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            assertEquals(LocalTime.of(0, 5), groups.updateTopicTime(7L, LocalTime.of(0, 5)))
            assertEquals("GET", server.takeRequest().method)
            val patch = server.takeRequest()
            assertEquals("PATCH", patch.method)
            assertEquals("/api/groups/7/settings", patch.path)
            val body = patch.body.readUtf8()
            assertTrue(body.contains("\"slotNo\":1,\"publishTime\":\"00:05\",\"deadlineTime\":\"12:00\""))
            assertTrue(body.contains("\"slotNo\":2,\"publishTime\":\"13:00\",\"deadlineTime\":\"18:00\""))
            assertFalse(body.contains("\"slotNo\":3"))
        }
    }

    @Test fun defaultFirstSlotDoesNotSendItsDefaultDeadline() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(response(detail.replace("\"custom\":true", "\"custom\":false")))
            server.enqueue(response(detail))
            val groups = GroupRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            groups.updateTopicTime(7L, LocalTime.NOON)
            server.takeRequest()
            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("\"publishTime\":\"12:00\""))
            assertFalse(body.contains("\"deadlineTime\""))
        }
    }

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

    @Test fun loadAllUsesTodayMissionsAndFallsBackToLegacyField() = runBlocking {
        MockWebServer().use { server ->
            val first = """{"missionId":10,"media":"TEXT","content":"첫째","submittedCount":1,"submittedByMe":true}"""
            val second = """{"missionId":20,"media":"DRAW","content":"둘째","submittedCount":0,"submittedByMe":false}"""
            var list = """{"content":[{"id":7,"name":"친구들","relationType":"FRIEND",
                "memberCount":2,"maxMembers":6,"members":$members,
                "todayMission":$first,"todayMissions":[$first,$second]}]}"""
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                    "/api/groups?page=0&size=100" -> response(list)
                    "/api/groups/7" -> response(detail)
                    else -> MockResponse().setResponseCode(500)
                }
            }
            val repo = GroupRepository(ApiClient(server.url("/").toString(), InMemoryTokenStore()))
            assertEquals(listOf(10L, 20L), repo.loadAll(8L).single().missions.map { it.missionId })
            list = """{"content":[{"id":7,"name":"친구들","relationType":"FRIEND",
                "memberCount":2,"maxMembers":6,"members":$members,"todayMission":$first}]}"""
            assertEquals(listOf(10L), repo.loadAll(8L).single().missions.map { it.missionId })
            list = """{"content":[{"id":7,"name":"친구들","relationType":"FRIEND",
                "memberCount":2,"maxMembers":6,"members":$members}]}"""
            assertTrue(repo.loadAll(8L).single().missions.isEmpty())
        }
    }

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
