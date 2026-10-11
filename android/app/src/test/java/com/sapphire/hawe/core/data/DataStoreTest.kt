package com.sapphire.hawe.core.data

import com.sapphire.hawe.core.model.*
import com.sapphire.hawe.designsystem.assets.Relationship
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

class DataStoreTest {
    private val now = Instant.parse("2026-10-12T03:00:00Z")
    private class AdjustableClock(var current: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = AdjustableClock(current, zone)
        override fun instant(): Instant = current
    }
    private fun store(clock: Clock = Clock.fixed(now, ZoneOffset.UTC), seed: Int = 7) =
        DataStore(Files.createTempDirectory("hawe-test").toFile(), clock, Random(seed))

    @Test fun createAndRefreshMission() {
        val store = store()
        val id = store.createGroup("우리", Relationship.FRIEND)
        assertEquals(1, store.groups.size)
        assertTrue(store.group(id)!!.amIOwner)
        assertNull(store.group(id)!!.startDate)
        assertEquals(1, store.group(id)!!.missionCount)
        assertTrue(store.group(id)!!.inviteCode.matches(Regex("[A-HJ-NP-Z2-9]{6}")))
        assertEquals(InviteLookupError.ALREADY_MEMBER,
            (store.lookUpInvite(store.group(id)!!.inviteCode) as InviteLookupResult.Failure).error)
        store.refreshTodayMissions()
        assertEquals(1, store.group(id)!!.missionCount)
        store.refreshTodayMissions(Instant.parse("2026-10-13T00:00:00Z"))
        assertEquals(2, store.group(id)!!.missionCount)
    }

    @Test fun inviteLookupAndJoin() {
        val store = store()
        assertTrue(store.lookUpInvite("K7Q2PX") is InviteLookupResult.Success)
        assertEquals(InviteLookupError.NOT_FOUND, (store.lookUpInvite("ZZZZZZ") as InviteLookupResult.Failure).error)
        assertEquals(InviteLookupError.FULL, (store.lookUpInvite("FULL00") as InviteLookupResult.Failure).error)
        val id = store.join("K7Q2PX")!!
        assertEquals(5, store.group(id)!!.members.size)
        assertEquals(InviteLookupError.ALREADY_MEMBER, (store.lookUpInvite("K7Q2PX") as InviteLookupResult.Failure).error)
        assertNull(store.join("K7Q2PX"))
        assertTrue(store.groups.first().missions.size > 3)
        val loverStore = store()
        val loverID = loverStore.join("LOVE22")!!
        assertEquals(2, loverStore.group(loverID)!!.members.size)
        assertEquals(InviteLookupError.ALREADY_MEMBER,
            (loverStore.lookUpInvite("LOVE22") as InviteLookupResult.Failure).error)
    }

    @Test fun submitResultsAndComments() {
        val store = store()
        val id = store.createGroup("우리", Relationship.FRIEND)
        val mission = store.todayMission(id)!!
        assertNull(store.result(id, mission.id))
        store.submit(SubmissionContent.Text("안녕"), id)
        assertTrue(store.todayMission(id)!!.didSubmit)
        store.submit(SubmissionContent.Text("다시"), id)
        val result = store.result(id, mission.id)!!
        assertEquals(1, result.submissions.size)
        assertEquals("안녕", result.submissions.first().text)
        val submission = result.submissions.first()
        store.toggleLike(submission.id, mission.id, id)
        val liked = store.result(id, mission.id)!!.submissions.first()
        assertEquals(1, liked.likeCount)
        assertNotSame(submission, liked)
        assertEquals(0, submission.likeCount)
        store.toggleLike(submission.id, mission.id, id)
        assertEquals(0, store.result(id, mission.id)!!.submissions.first().likeCount)
        store.addComment("댓글", submission.id, mission.id, id)
        val comment = store.result(id, mission.id)!!.submissions.first().comments.single()
        assertTrue(submission.comments.isEmpty())
        store.deleteComment(comment.id, submission.id, mission.id, id)
        assertTrue(store.result(id, mission.id)!!.submissions.first().comments.isEmpty())

        val foreign = SubmissionComment(UUID.randomUUID(), store.myProfile, false, "남의 댓글", now)
        val current = store.groups.single().missions.single()
        store.replaceMissionsForTest(id, listOf(current.copy(submissions = listOf(
            current.submissions.single().copy(comments = listOf(foreign))))))
        store.deleteComment(foreign.id, submission.id, mission.id, id)
        assertEquals(listOf(foreign), store.result(id, mission.id)!!.submissions.first().comments)
    }

    @Test fun imageAndProfileAndReset() {
        val store = store()
        val firstDay = LocalDate.of(2026, 5, 1)
        val id = store.createGroup("우리", Relationship.LOVER, firstDay)
        assertEquals(firstDay, store.group(id)!!.startDate)
        store.submit(SubmissionContent.Image(byteArrayOf(1, 2, 3), "png"), id)
        val uri = store.result(id, store.todayMission(id)!!.id)!!.submissions.first().mediaURL!!
        assertEquals(3, java.io.File(uri).readBytes().size)
        val changed = Profile(store.myProfile.avatar, "새 이름")
        store.setMyProfile(changed)
        assertEquals(changed, store.group(id)!!.me!!.defaultProfile)
        store.leave(id)
        assertTrue(store.groups.isEmpty())
        store.createGroup("다시", Relationship.FRIEND)
        store.reset()
        assertTrue(store.groups.isEmpty())
        assertEquals(Profile.mockMine, store.myProfile)
    }

    @Test fun recordsHideUnsubmittedCurrentDayAndIncludeMySubmission() {
        val store = store()
        val id = store.join("K7Q2PX")!!
        assertTrue(store.records(id).isNotEmpty())
        assertTrue(store.records(id).all { it.date < store.todayMission(id)!!.opensAt })
        store.submit(SubmissionContent.Text("오늘"), id)
        assertTrue(store.records(id).any { it.missionID == store.todayMission(id)!!.id && it.didSubmit })
    }

    @Test fun settingsAndPokeReplaceSnapshots() {
        val store = store()
        val id = store.createGroup("우리", Relationship.FRIEND)
        val before = store.group(id)!!
        store.setNotify(false, id)
        assertTrue(before.notify)
        assertFalse(store.group(id)!!.notify)
        store.setTopicTime(LocalTime.NOON, id)
        assertEquals(LocalTime.NOON, store.group(id)!!.topicTime)
        val changed = Profile(store.myProfile.avatar, "그룹 별명")
        store.setMyGroupProfile(changed, id)
        assertEquals(changed, store.group(id)!!.me!!.displayed)
        assertEquals(Profile.mockMine, before.me!!.displayed)
        val memberID = store.group(id)!!.me!!.id
        store.poke(memberID, id)
        assertTrue(store.todayMission(id)!!.members.single().pokedToday)
        store.poke(memberID, id)
        assertEquals(setOf(memberID), store.groups.single().missions.single().poked)
    }

    @Test fun missionRulesAcrossRandomSeeds() {
        repeat(20) { seed ->
            val start = now.minusSeconds(30L * 86400)
            val clock = AdjustableClock(start)
            val store = store(clock, seed)
            val id = store.createGroup("우리", Relationship.FRIEND)
            repeat(30) { day ->
                clock.current = start.plusSeconds((day + 1L) * 86400)
                store.refreshTodayMissions()
            }
            val missions = store.groups.single().missions
            assertEquals(31, missions.size)
            missions.windowed(3).forEach { window ->
                assertFalse("seed=$seed", window.all { it.type == window.first().type })
            }
            missions.forEachIndexed { index, mission ->
                val previousTopics = missions.take(index)
                    .filter { java.time.Duration.between(it.opensAt, mission.opensAt).toDays() < 30 }
                    .map { it.topic }.toSet()
                val available = TopicPool.topics(mission.type).filterNot { it in previousTopics }
                if (available.isNotEmpty()) assertTrue("seed=$seed index=$index", mission.topic in available)
            }
            assertEquals(31, store.group(id)!!.missionCount)
        }
    }

    @Test fun streakUsesSeoulDays() {
        val clock = AdjustableClock(now.minusSeconds(3L * 86400))
        val store = store(clock)
        val id = store.createGroup("우리", Relationship.FRIEND)
        repeat(3) { index ->
            store.submit(SubmissionContent.Text("day $index"), id)
            clock.current = clock.current.plusSeconds(86400)
            store.refreshTodayMissions()
        }
        assertEquals(3, store.stats.completedMissions)
        assertEquals(3, store.stats.streakDays)
        assertFalse(store.todayMission(id)!!.didSubmit)
    }
}
