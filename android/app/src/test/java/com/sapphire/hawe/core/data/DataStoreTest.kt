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
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

class DataStoreTest {
    @Test fun openMissionsKeepIndependentStateAndSummary() {
        val store = store()
        val detail = GroupDetail.mock.copy(id = 11L)
        val summaries = listOf(MissionSummary(10, MissionType.WRITE, "같은 주제", 0, false),
            MissionSummary(20, MissionType.WRITE, "같은 주제", 1, true))
        store.applyServerGroups(listOf(LoadedGroup(detail, summaries)))
        store.applyCurrentMission(11, CurrentMissionState(listOf(serverMission(10),
            serverMission(20).copy(submittedByMe = true)), null))
        assertEquals(listOf(10L, 20L), store.todayMissions(11).map { it.id })
        assertEquals(10L, store.todayMission(11)?.id)
        assertEquals(20L, store.todayMission(11, 20)?.id)
        assertNull(store.todayMission(11, 99))
        assertEquals(2, store.summaries.single().missionCount)
        assertEquals(1, store.summaries.single().mySubmittedCount)
        assertFalse(store.summaries.single().didSubmit)
        val me = detail.me!!
        val mine = Submission(100, me.displayed, true, MissionType.WRITE, now,
            text = "답", authorID = me.id)
        store.applySubmitted(11, 10, mine)
        store.applyFeed(11, 20, listOf(mine.copy(id = 200)))
        store.markPoked(11, 20, -2, PokeKind.POKE)
        assertTrue(store.todayMission(11, 10)!!.didSubmit)
        assertTrue(store.todayMission(11, 20)!!.didSubmit)
        assertEquals(1, store.groups.single().missions.first().submissions.size)
        assertEquals(200L, store.groups.single().missions.last().submissions.single().id)
        assertFalse(store.groups.single().missions.first().feedLoaded)
        assertTrue(store.groups.single().missions.last().feedLoaded)
        assertTrue(store.groups.single().missions.first().poked.isEmpty())
        assertEquals(setOf(PokeKind.POKE), store.groups.single().missions.last().poked[-2L])
        assertEquals(2, store.summaries.single().mySubmittedCount)
        assertTrue(store.summaries.single().didSubmit)
        store.applyCurrentMission(11, CurrentMissionState(listOf(serverMission(10), serverMission(20),
            serverMission(30)), null))
        assertEquals(listOf(10L, 20L, 30L), store.todayMissions(11).map { it.id })
        assertTrue(store.groups.single().missions[1].feedLoaded)
        assertTrue(store.groups.single().missions[1].serverSubmittedByMe)
        assertEquals(setOf(PokeKind.POKE), store.groups.single().missions[1].poked[-2L])
        assertFalse(store.groups.single().missions[2].feedLoaded)
        store.applyCurrentMission(11, CurrentMissionState(listOf(serverMission(20), serverMission(30)), null))
        assertEquals(listOf(20L, 30L), store.todayMissions(11).map { it.id })
        store.applySubmitted(11, 10, mine)
        store.markPoked(11, 10, -2, PokeKind.FLING)
        store.applyFeed(11, 10, emptyList())
        assertEquals(listOf(20L, 30L), store.todayMissions(11).map { it.id })
    }
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

    @Test fun calendarRecordsAreKeptPerGroupAndMonth() {
        val store = store()
        val october = YearMonth.of(2026, 10)
        assertNull(store.records(11L, october))
        val record = DayRecord(now, MissionType.PHOTO, "오늘의 하늘", 3, 4, true, 101L, Stamp.GOOD)
        store.applyCalendar(11L, october, listOf(record))
        assertEquals(listOf(record), store.records(11L, october))
        assertNull(store.records(11L, YearMonth.of(2026, 9)))
        assertNull(store.records(12L, october))
        assertEquals(record, store.calendarRecord(11L, 101L))
        assertNull(store.calendarRecord(12L, 101L))
        store.reset()
        assertNull(store.records(11L, october))
    }

    @Test fun pastMissionResultUsesCalendarRecordAndFeed() {
        val store = store()
        val detail = GroupDetail.mock.copy(id = 11L)
        store.applyServerGroups(listOf(LoadedGroup(detail, null)))
        store.applyCurrentMission(11L, CurrentMissionState(serverMission(200L), now.plusSeconds(86400)))
        val pastDay = now.minusSeconds(3L * 86400)
        store.applyCalendar(11L, YearMonth.of(2026, 10),
            listOf(DayRecord(pastDay, MissionType.PHOTO, "지난 주제", 1, 4, true, 101L, Stamp.LATE)))
        // 피드를 받기 전에는 결과가 없음
        assertNull(store.result(11L, 101L))
        val me = detail.me!!
        val mine = Submission(500L, me.displayed, true, MissionType.PHOTO, pastDay, isLate = true, authorID = me.id)
        store.applyFeed(11L, 101L, listOf(mine))
        val result = store.result(11L, 101L)!!
        assertEquals("지난 주제", result.topic)
        assertEquals(MissionType.PHOTO, result.type)
        assertEquals(4, result.memberCount)
        assertEquals(listOf(mine), result.submissions)
        assertFalse(result.pending.contains(me.displayed))
        // 지난 미션 피드가 현재 미션을 건드리지 않음
        assertFalse(store.groups.single().missions.single().feedLoaded)
    }

    @Test fun pastAggregateMissionLearnsTemplateAndSpec() {
        val store = store()
        val detail = GroupDetail.mock.copy(id = 11L)
        store.applyServerGroups(listOf(LoadedGroup(detail, null)))
        store.applyCurrentMission(11L, CurrentMissionState(serverMission(200L).copy(type = MissionType.SLIDER,
            template = "SLIDER_POSITION", spec = MissionSpec(question = "컨디션", left = "방전", right = "풀충전")),
            now.plusSeconds(86400)))
        // 현재 미션은 틀·빈칸을 이미 알아서 다시 받을 필요가 없어요
        assertFalse(store.needsMissionSpec(11L, 200L))
        val me = detail.me!!
        store.applyFeed(11L, 200L, listOf(Submission(1L, me.displayed, true, MissionType.SLIDER, now,
            authorID = me.id, template = "SLIDER_POSITION", number = 70)))
        val current = store.result(11L, 200L)!!
        assertEquals("SLIDER_POSITION", current.template)
        assertEquals("풀충전", current.spec.right)

        // 달력에서 연 지난 밸런스 미션: 틀은 제출물에서, 선택지는 미션 정보를 받은 뒤에 알아요
        val pastDay = now.minusSeconds(2L * 86400)
        store.applyCalendar(11L, YearMonth.of(2026, 10),
            listOf(DayRecord(pastDay, MissionType.WRITE, "짜장면 vs 짬뽕", 2, 4, true, 101L, Stamp.GOOD)))
        assertTrue(store.needsMissionSpec(11L, 101L))
        assertFalse(store.needsMissionSpec(99L, 101L))  // 모르는 그룹
        store.applyFeed(11L, 101L, listOf(Submission(2L, me.displayed, true, MissionType.WRITE, pastDay,
            authorID = me.id, template = "TEXT_BALANCE", choice = "A")))
        val before = store.result(11L, 101L)!!
        assertEquals("TEXT_BALANCE", before.template)
        assertEquals(MissionSpec(), before.spec)
        store.applyMissionSpec(101L, "TEXT_BALANCE", MissionSpec(optionA = "짜장면", optionB = "짬뽕"))
        assertFalse(store.needsMissionSpec(11L, 101L))
        assertEquals("짜장면", store.result(11L, 101L)!!.spec.optionA)
        store.reset()
        store.applyServerGroups(listOf(LoadedGroup(detail, null)))
        assertTrue(store.needsMissionSpec(11L, 101L))  // 로그아웃하면 지워요
    }

    @Test fun settingsReplaceSnapshots() {
        val store = store()
        val id = store.createGroup("우리", Relationship.FRIEND)
        injectMission(store, id, MissionType.DRAW)
        val before = store.group(id)!!
        store.setNotify(false, id)
        assertTrue(before.notify)
        assertFalse(store.group(id)!!.notify)
        store.setTopicTime(LocalTime.NOON, id)
        assertEquals(LocalTime.NOON, store.group(id)!!.topicTime)
    }

    @Test fun pokesComeFromServerAndSurviveLateRefresh() {
        val store = store()
        store.applyServerGroups(listOf(LoadedGroup(GroupDetail.mock.copy(id = 11L), null)))
        fun poked(memberID: Long) = store.todayMission(11L)!!.members.single { it.id == memberID }.poked
        store.applyCurrentMission(11L, CurrentMissionState(
            serverMission(10L).copy(pokedByMe = mapOf(-2L to setOf(PokeKind.POKE))), null))
        assertEquals(setOf(PokeKind.POKE), poked(-2L))
        assertEquals(emptySet<PokeKind>(), poked(-3L))
        store.markPoked(11L, 10L, -2L, PokeKind.FLING)
        store.markPoked(11L, 10L, -3L, PokeKind.FLING)
        store.markPoked(11L, 10L, -3L, PokeKind.FLING)
        assertEquals(setOf(PokeKind.POKE, PokeKind.FLING), poked(-2L))
        assertEquals(setOf(PokeKind.FLING), poked(-3L))
        // 보내기 전에 출발한 새로고침이 늦게 와도 방금 보낸 표시는 남는다
        store.applyCurrentMission(11L, CurrentMissionState(serverMission(10L), null))
        assertEquals(setOf(PokeKind.POKE, PokeKind.FLING), poked(-2L))
        assertEquals(setOf(PokeKind.FLING), poked(-3L))
        // 그사이 미션이 바뀌었으면 이전 미션의 응답은 반영하지 않는다
        store.markPoked(11L, 9L, -4L, PokeKind.POKE)
        assertEquals(emptySet<PokeKind>(), poked(-4L))
        // 새 미션은 처음부터
        store.applyCurrentMission(11L, CurrentMissionState(serverMission(12L), null))
        assertTrue(store.todayMission(11L)!!.members.all { it.poked.isEmpty() })
    }

    @Test fun statsComeFromServerAndResetOnSignOut() {
        val store = store()
        assertNull(store.stats)
        val stats = ActivityStats(3, 2, 1, mapOf(MissionType.WRITE to 3))
        store.applyStats(stats)
        assertEquals(stats, store.stats)
        store.reset()
        assertNull(store.stats)
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
