package com.sapphire.hawe.core.data

import com.sapphire.hawe.core.model.*
import com.sapphire.hawe.core.net.CurrentMissionState
import com.sapphire.hawe.core.net.ServerMission
import com.sapphire.hawe.core.net.LoadedGroup
import com.sapphire.hawe.core.net.MissionSummary
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
import kotlin.random.Random

class DataStoreTest {
    @Test fun serverGroupsPreserveLocalMissionsAndState() {
        val store = store()
        assertEquals(GroupsState.NOT_LOADED, store.groupsState)
        store.markGroupsFailed()
        assertEquals(GroupsState.FAILED, store.groupsState)
        val local = store.createGroup("local", Relationship.FRIEND)
        assertTrue(local < 0)
        val first = GroupDetail.mock.copy(id = 11L, name = "first")
        val second = GroupDetail.mock.copy(id = 12L, name = "second")
        store.applyServerGroups(listOf(LoadedGroup(first, null), LoadedGroup(second, null)))
        assertEquals(listOf(11L, 12L), store.groups.map { it.id })
        assertEquals(GroupsState.LOADED, store.groupsState)
        store.applyCurrentMission(11L, CurrentMissionState(serverMission(101L), now.plusSeconds(86400)))
        val mission = store.groups.first().missions.single()
        store.applyServerGroups(listOf(LoadedGroup(second, null), LoadedGroup(first.copy(name = "updated"), null)))
        assertEquals(listOf(12L, 11L), store.groups.map { it.id })
        assertEquals(mission, store.groups.last().missions.single())
        assertEquals("updated", store.group(11L)!!.name)
        store.applyServerGroup(first.copy(name = "again"))
        assertEquals("again", store.group(11L)!!.name)
        store.markGroupsFailed()
        assertEquals(GroupsState.LOADED, store.groupsState)
        store.leave(11L)
        store.leave(12L)
        assertEquals(GroupsState.LOADED, store.groupsState)
        store.reset()
        assertEquals(GroupsState.NOT_LOADED, store.groupsState)
    }
    private val now = Instant.parse("2026-10-12T03:00:00Z")
    private class AdjustableClock(var current: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = AdjustableClock(current, zone)
        override fun instant(): Instant = current
    }
    private fun store(clock: Clock = Clock.fixed(now, ZoneOffset.UTC), seed: Int = 7) =
        DataStore(Files.createTempDirectory("hawe-test").toFile(), clock, Random(seed))

    @Test fun localGroupsDoNotGenerateMissions() {
        val store = store()
        val id = store.createGroup("우리", Relationship.FRIEND)
        assertEquals(1, store.groups.size)
        assertTrue(store.group(id)!!.amIOwner)
        assertNull(store.group(id)!!.startDate)
        assertEquals(0, store.group(id)!!.missionCount)
        assertTrue(store.group(id)!!.inviteCode.matches(Regex("[A-HJ-NP-Z2-9]{6}")))
        assertEquals(InviteLookupError.ALREADY_MEMBER,
            (store.lookUpInvite(store.group(id)!!.inviteCode) as InviteLookupResult.Failure).error)
        assertNull(store.todayMission(id))
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

    @Test fun feedAndSubmittedStateComeFromServer() {
        val store = store()
        val detail = GroupDetail.mock.copy(id = 11L)
        store.applyServerGroups(listOf(LoadedGroup(detail, MissionSummary(10L, MissionType.WRITE,
            "서버 주제", 0, false))))
        store.applyCurrentMission(11L, CurrentMissionState(serverMission(10L), now.plusSeconds(86400)))
        assertFalse(store.todayMission(11L)!!.didSubmit)
        assertNull(store.result(11L, 10L))
        val me = detail.me!!
        val mine = Submission(100L, me.displayed, true, MissionType.WRITE, now,
            text = "안녕", authorID = me.id)
        store.applySubmitted(11L, 10L, mine)
        store.applySubmitted(11L, 10L, mine)
        assertTrue(store.todayMission(11L)!!.didSubmit)
        assertTrue(store.todayMission(11L)!!.members.first { it.isMe }.isDone)
        assertEquals(1, store.summaries.single().submittedCount)
        assertTrue(store.summaries.single().didSubmit)
        assertNull(store.result(11L, 10L))
        store.applyFeed(11L, 10L, listOf(mine))
        val result = store.result(11L, 10L)!!
        assertEquals(listOf(mine), result.submissions)
        assertEquals(detail.members.size - 1, result.pending.size)
        store.applyFeed(11L, 11L, emptyList())
        assertEquals(listOf(mine), store.result(11L, 10L)!!.submissions)
    }

    @Test fun profileAndReset() {
        val store = store()
        val firstDay = LocalDate.of(2026, 5, 1)
        val id = store.createGroup("우리", Relationship.LOVER, firstDay)
        injectMission(store, id, MissionType.PHOTO)
        assertEquals(firstDay, store.group(id)!!.startDate)
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
        val mission = store.todayMission(id)!!
        val member = store.group(id)!!.me!!
        store.applySubmitted(id, mission.id, Submission(100L, member.displayed, true,
            MissionType.WRITE, now, text = "오늘", authorID = member.id))
        assertTrue(store.records(id).any { it.missionID == store.todayMission(id)!!.id && it.didSubmit })
    }

    @Test fun settingsAndPokeReplaceSnapshots() {
        val store = store()
        val id = store.createGroup("우리", Relationship.FRIEND)
        injectMission(store, id, MissionType.DRAW)
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
        assertEquals(setOf(memberID), store.groups.single().missions.single().poked)
        store.poke(memberID, id)
        assertEquals(setOf(memberID), store.groups.single().missions.single().poked)
    }

    @Test fun streakUsesSeoulDays() {
        val clock = AdjustableClock(now.minusSeconds(3L * 86400))
        val store = store(clock)
        repeat(3) { index ->
            val id = store.createGroup("우리$index", Relationship.FRIEND)
            injectMission(store, id, MissionType.WRITE, clock.current)
            val mission = store.todayMission(id)!!
            store.replaceMissionsForTest(id, listOf(store.groups.last().missions.single().copy(
                serverSubmittedByMe = true)))
            clock.current = clock.current.plusSeconds(86400)
        }
        assertEquals(3, store.stats.completedMissions)
        assertEquals(3, store.stats.streakDays)
    }

    @Test fun currentMissionPreservesMatchingServerFeedAndResetsOnNewMission() {
        val store = store()
        val detail = GroupDetail.mock.copy(id = 11L)
        store.applyServerGroups(listOf(LoadedGroup(detail, MissionSummary(10L, MissionType.WRITE,
            "서버 주제", 0, false))))
        store.applyCurrentMission(11L, CurrentMissionState(serverMission(10L), now.plusSeconds(86400)))
        assertEquals(MissionState.LOADED, store.groups.single().missionState)
        assertEquals("서버 주제", store.summaries.single().topic)
        val member = detail.me!!
        store.applySubmitted(11L, 10L, Submission(100L, member.displayed, true,
            MissionType.WRITE, now, text = "답", authorID = member.id))
        store.applyFeed(11L, 10L, store.groups.single().missions.single().submissions)
        assertTrue(store.todayMission(11L)!!.didSubmit)
        assertEquals(1, store.summaries.single().submittedCount)
        store.applyCurrentMission(11L, CurrentMissionState(serverMission(10L), now.plusSeconds(86400)))
        assertTrue(store.todayMission(11L)!!.didSubmit)
        assertTrue(store.groups.single().missions.single().feedLoaded)
        assertEquals(1, store.result(11L, 10L)!!.submissions.size)
        store.applyCurrentMission(11L, CurrentMissionState(serverMission(11L), now.plusSeconds(86400)))
        assertFalse(store.todayMission(11L)!!.didSubmit)
        assertFalse(store.groups.single().missions.single().feedLoaded)
        assertTrue(store.groups.single().missions.single().submissions.isEmpty())
        store.applyCurrentMission(11L, CurrentMissionState(null, now.plusSeconds(86400)))
        assertNull(store.todayMission(11L))
        assertEquals(now.plusSeconds(86400), store.groups.single().nextPublishAt)
    }

    private fun serverMission(id: Long) = ServerMission(id, 1, "서버 주제", MissionType.WRITE,
        "TEXT_ACROSTIC", MissionSpec(word = "고양이"), now, now.plusSeconds(3600),
        now.plusSeconds(86400), false, mapOf(1L to false))

    private fun injectMission(store: DataStore, id: Long, type: MissionType, at: Instant = now) {
        store.replaceMissionsForTest(id, listOf(StoredMission(-100L, type, "주제", 1, at,
            at.plusSeconds(86400))))
    }
}
