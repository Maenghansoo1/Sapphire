package com.sapphire.hawe.core.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sapphire.hawe.core.model.*
import com.sapphire.hawe.core.net.CurrentMissionState
import com.sapphire.hawe.core.net.LoadedGroup
import com.sapphire.hawe.core.net.MissionSummary
import com.sapphire.hawe.designsystem.assets.Relationship
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlin.random.Random

sealed interface SubmissionContent {
    data class Image(val bytes: ByteArray, val fileExtension: String) : SubmissionContent
    data class Text(val body: String) : SubmissionContent
    /** 새 틀(한 단어·후기·밸런스)의 글·숫자·선택 필드. null 인 필드는 보내지 않아요. */
    data class Scalar(val number: Int? = null, val choice: String? = null,
        val text: String? = null, val caption: String? = null) : SubmissionContent
}

enum class InviteLookupError(val message: String) {
    NOT_FOUND("초대 코드를 찾을 수 없어요. 다시 확인해 주세요."),
    FULL("이 그룹은 정원이 다 찼어요."),
    ALREADY_MEMBER("이미 참여 중인 그룹이에요."),
    TOO_MANY("시도가 너무 많아요. 잠시 후 다시 시도해 주세요"),
    NETWORK("네트워크 연결을 확인해 주세요"),
    UNAVAILABLE("잠시 후 다시 시도해 주세요"),
}

enum class GroupsState { NOT_LOADED, LOADED, FAILED }
enum class MissionState { NOT_LOADED, LOADED, FAILED }

sealed interface InviteLookupResult {
    data class Success(val preview: GroupInvitePreview) : InviteLookupResult
    data class Failure(val error: InviteLookupError) : InviteLookupResult
}

data class StoredGroup(val detail: GroupDetail, val missions: List<StoredMission> = emptyList(),
    val summaries: List<MissionSummary> = emptyList(), val nextPublishAt: Instant? = null,
    val missionState: MissionState = MissionState.NOT_LOADED) {
    val id: Long get() = detail.id
    val summary: MissionSummary? get() = summaries.firstOrNull()
}

data class StoredMission(
    val id: Long, val type: MissionType?, val topic: String, val number: Int,
    val opensAt: Instant, val closesAt: Instant,
    val submissions: List<Submission> = emptyList(),
    val poked: Map<Long, Set<PokeKind>> = emptyMap(),
    val deadlineAt: Instant = closesAt, val template: String = "", val spec: MissionSpec = MissionSpec(),
    val serverMembers: Map<Long, Boolean> = emptyMap(), val serverSubmittedByMe: Boolean = false,
    val feedLoaded: Boolean = false,
)

class DataStore(
    private val mediaDir: File,
    private val clock: Clock = Clock.systemUTC(),
    private val random: Random = Random.Default,
) {
    private val _groups = mutableStateListOf<StoredGroup>()
    var groupsState by mutableStateOf(GroupsState.NOT_LOADED)
        private set
    private var nextLocalGroupId = -1L
    private var nextLocalMemberId = -1L
    private fun nextLocalId(): Long = nextLocalGroupId--
    private fun nextLocalMember(): Long = nextLocalMemberId--
    val groups: List<StoredGroup> get() = _groups.toList()
    private var storedProfile by mutableStateOf(Profile.mockMine)
    val myProfile: Profile get() = storedProfile
    // 달력: (그룹, 달) → 그 달 기록. 달력에서 연 지난 미션의 피드는 pastFeeds에 따로 둬요.
    private val calendars = mutableStateMapOf<Pair<Long, YearMonth>, List<DayRecord>>()
    private val pastFeeds = mutableStateMapOf<Long, List<Submission>>()
    // 달력에서 연 지난 미션의 틀·빈칸(밸런스 선택지, 그림 대상). 달력 응답에는 없어서 따로 받아 둬요.
    private val pastSpecs = mutableStateMapOf<Long, Pair<String, MissionSpec>>()
    var stats by mutableStateOf<ActivityStats?>(null)
        private set

    fun setMyProfile(profile: Profile) {
        storedProfile = profile
        _groups.indices.forEach { index ->
            val group = _groups[index]
            val members = group.detail.members.map { if (it.isMe) it.copy(defaultProfile = profile) else it }
            _groups[index] = group.copy(detail = group.detail.copy(members = members))
        }
    }

    fun reset() {
        _groups.clear(); storedProfile = Profile.mockMine; groupsState = GroupsState.NOT_LOADED
        calendars.clear(); pastFeeds.clear(); pastSpecs.clear(); stats = null
        nextLocalGroupId = -1L; nextLocalMemberId = -1L
    }

    fun applyServerGroups(details: List<LoadedGroup>) {
        val old = _groups.associateBy { it.id }
        _groups.clear()
        _groups.addAll(details.map { loaded ->
            val previous = old[loaded.detail.id]
            StoredGroup(loaded.detail, previous?.missions.orEmpty(), loaded.missions,
                previous?.nextPublishAt, previous?.missionState ?: MissionState.NOT_LOADED)
        })
        groupsState = GroupsState.LOADED
    }

    fun applyServerGroup(detail: GroupDetail) {
        val index = _groups.indexOfFirst { it.id == detail.id }
        val stored = if (index < 0) StoredGroup(detail) else _groups[index].copy(detail = detail)
        if (index < 0) _groups.add(stored) else _groups[index] = stored
    }

    fun markGroupsFailed() {
        if (groupsState == GroupsState.NOT_LOADED) groupsState = GroupsState.FAILED
    }

    fun applyCurrentMission(groupID: Long, state: CurrentMissionState) = update(groupID) { group ->
        val previousById = group.missions.associateBy { it.id }
        val current = state.missions.map { row ->
            val previous = previousById[row.id]?.takeIf { it.topic == row.topic &&
                it.template == row.template && it.spec == row.spec }
            StoredMission(row.id, row.type, row.topic, row.number,
                row.publishedAt, row.closesAt, previous?.submissions.orEmpty(),
                mergePoked(row.pokedByMe, previous?.poked.orEmpty()), row.deadlineAt, row.template, row.spec,
                row.members + previous?.serverMembers.orEmpty().filterValues { done -> done },
                row.submittedByMe || previous?.serverSubmittedByMe == true, previous?.feedLoaded == true)
        }
        val byId = state.missions.associateBy { it.id }
        val summaries = group.summaries.map { summary -> byId[summary.missionId]?.let { row ->
            MissionSummary(row.id, row.type, row.topic, row.members.count { it.value }, row.submittedByMe)
        } ?: summary }
        group.copy(missions = current, summaries = summaries, nextPublishAt = state.nextPublishAt,
            missionState = MissionState.LOADED)
    }

    fun clearMissionForReroll(groupID: Long, missionID: Long) = update(groupID) { group ->
        group.copy(missions = group.missions.map { mission -> if (mission.id != missionID) mission else
            mission.copy(submissions = emptyList(), poked = emptyMap(), serverMembers = emptyMap(),
                serverSubmittedByMe = false, feedLoaded = false) },
            summaries = group.summaries.map { summary -> if (summary.missionId != missionID) summary else
                summary.copy(submittedCount = 0, submittedByMe = false) })
    }

    fun markMissionFailed(groupID: Long) = update(groupID) {
        if (it.missions.isNotEmpty()) it else it.copy(missionState = MissionState.FAILED)
    }

    fun applyStats(stats: ActivityStats) { this.stats = stats }

    fun applyCalendar(groupID: Long, month: YearMonth, records: List<DayRecord>) {
        calendars[groupID to month] = records.toList()
    }

    /** 아직 불러오지 않은 달이면 null */
    fun records(groupID: Long, month: YearMonth): List<DayRecord>? = calendars[groupID to month]

    fun calendarRecord(groupID: Long, missionID: Long): DayRecord? = calendars.entries
        .filter { it.key.first == groupID }.flatMap { it.value }.firstOrNull { it.missionID == missionID }

    fun group(id: Long): GroupDetail? = groups.firstOrNull { it.id == id }?.detail

    val summaries: List<GroupSummary> get() = groups.map { stored ->
        val detail = stored.detail
        val summary = stored.summary
        val missionCount = stored.summaries.size
        val mySubmittedCount = stored.summaries.count { it.submittedByMe }
        GroupSummary(detail.id, detail.name, detail.relationship, detail.members.map { it.displayed.avatar },
            detail.startDate, summary?.type, summary?.topic,
            summary?.submittedCount ?: 0, missionCount > 0 && mySubmittedCount == missionCount,
            missionCount, mySubmittedCount)
    }

    // Local simulation for tests and previews only. Production uses GroupRepository.
    internal fun createGroup(name: String, relationship: Relationship, startDate: LocalDate? = null): Long {
        val detail = GroupDetail(nextLocalId(), name, relationship, Instant.now(clock), 0,
            makeInviteCode(), members = listOf(GroupMember(nextLocalMember(), myProfile, isMe = true, isOwner = true)),
            startDate = if (relationship == Relationship.LOVER) startDate else null)
        _groups.add(StoredGroup(detail))
        return detail.id
    }

    internal fun lookUpInvite(code: String): InviteLookupResult {
        if (groups.any { it.detail.inviteCode == code }) return InviteLookupResult.Failure(InviteLookupError.ALREADY_MEMBER)
        val demo = DemoSeed.group(code, Instant.now(clock)) ?: return InviteLookupResult.Failure(InviteLookupError.NOT_FOUND)
        val preview = GroupInvitePreview(demo.detail.name, demo.detail.relationship,
            demo.detail.members.map { it.displayed.avatar })
        return if (preview.isFull) InviteLookupResult.Failure(InviteLookupError.FULL) else InviteLookupResult.Success(preview)
    }

    internal fun join(code: String): Long? {
        if (lookUpInvite(code) !is InviteLookupResult.Success) return null
        val demo = DemoSeed.group(code, Instant.now(clock)) ?: return null
        val joined = demo.copy(detail = demo.detail.copy(members = demo.detail.members +
            GroupMember(nextLocalMember(), myProfile, isMe = true)))
        _groups.add(joined)
        return joined.id
    }

    fun leave(groupID: Long) { _groups.removeAll { it.id == groupID } }
    fun setTopicTime(time: LocalTime, groupID: Long) = update(groupID) {
        it.copy(detail = it.detail.copy(topicTime = time))
    }
    fun setNotify(on: Boolean, groupID: Long) = update(groupID) {
        it.copy(detail = it.detail.copy(notify = on))
    }
    fun todayMissions(groupID: Long): List<TodayMission> {
        val stored = groups.firstOrNull { it.id == groupID } ?: return emptyList()
        // DemoSeed keeps historical missions in local negative-ID groups; production holds only open missions.
        val current = if (groupID < 0) stored.missions.takeLast(1) else stored.missions
        return current.map { stored.toTodayMission(it) }
    }

    fun todayMission(groupID: Long): TodayMission? = todayMissions(groupID).firstOrNull()

    fun todayMission(groupID: Long, missionID: Long): TodayMission? =
        todayMissions(groupID).firstOrNull { it.id == missionID }

    private fun StoredGroup.toTodayMission(mission: StoredMission): TodayMission {
        return TodayMission(mission.id, detail, mission.type, mission.topic, mission.number,
            mission.opensAt, mission.closesAt, detail.members.map { member ->
                MissionMember(member.id, member.displayed, member.isMe,
                    mission.serverMembers[member.id] == true ||
                        (member.isMe && mission.serverSubmittedByMe),
                    mission.poked[member.id].orEmpty())
            }, mission.deadlineAt, mission.template, mission.spec)
    }

    fun applyFeed(groupID: Long, missionID: Long, submissions: List<Submission>) {
        val isCurrent = groups.firstOrNull { it.id == groupID }?.missions?.any { it.id == missionID } == true
        if (!isCurrent && calendarRecord(groupID, missionID) != null) {
            pastFeeds[missionID] = submissions.toList()
            return
        }
        applyCurrentFeed(groupID, missionID, submissions)
    }

    fun applyMissionSpec(missionID: Long, template: String, spec: MissionSpec) {
        pastSpecs[missionID] = template to spec
    }

    /** 결과 화면이 지난 미션의 틀·빈칸을 서버에서 받아 와야 하는지. 현재 미션은 이미 알고 있어요. */
    fun needsMissionSpec(groupID: Long, missionID: Long): Boolean {
        val stored = groups.firstOrNull { it.id == groupID } ?: return false
        return stored.missions.none { it.id == missionID } && missionID !in pastSpecs
    }

    private fun applyCurrentFeed(groupID: Long, missionID: Long, submissions: List<Submission>) = update(groupID) { group ->
        group.copy(missions = group.missions.map { mission ->
            if (mission.id == missionID) mission.copy(submissions = submissions.toList(), feedLoaded = true)
            else mission
        })
    }

    fun applySubmitted(groupID: Long, missionID: Long, submission: Submission) = update(groupID) { group ->
        val mission = group.missions.firstOrNull { it.id == missionID } ?: return@update group
        val myId = group.detail.me?.id
        val updated = mission.copy(serverSubmittedByMe = true,
            serverMembers = if (myId == null) mission.serverMembers else mission.serverMembers + (myId to true),
            submissions = if (mission.submissions.any { it.id == submission.id }) mission.submissions
                else mission.submissions + submission)
        val summaries = group.summaries.map {
            if (it.missionId == missionID) it.copy(
                submittedCount = it.submittedCount + if (it.submittedByMe) 0 else 1, submittedByMe = true)
            else it
        }
        group.copy(missions = group.missions.map { if (it.id == missionID) updated else it }, summaries = summaries)
    }

    /** 서버가 재촉을 받은 뒤에만 부른다. 그사이 미션이 바뀌었으면 반영하지 않는다. */
    fun markPoked(groupID: Long, missionID: Long, memberID: Long, kind: PokeKind) = update(groupID) { group ->
        group.copy(missions = group.missions.map { mission ->
            if (mission.id == missionID) mission.copy(
                poked = mergePoked(mission.poked, mapOf(memberID to setOf(kind)))) else mission
        })
    }

    // 재촉은 취소가 없어서 서버 값과 앱이 아는 값을 합친다(보낸 직후 늦게 도착한 새로고침이 표시를 지우지 않게).
    private fun mergePoked(server: Map<Long, Set<PokeKind>>, local: Map<Long, Set<PokeKind>>) =
        (server.keys + local.keys).associateWith { server[it].orEmpty() + local[it].orEmpty() }

    fun result(groupID: Long, missionID: Long): MissionResult? {
        val stored = groups.firstOrNull { it.id == groupID } ?: return null
        val mission = stored.missions.firstOrNull { it.id == missionID } ?: return pastResult(stored, missionID)
        if (!mission.feedLoaded) return null
        val done = mission.submissions.mapNotNull { it.authorID }.toSet()
        return MissionResult(mission.id, stored.detail.name, mission.type ?: return null, mission.topic, mission.opensAt,
            stored.detail.members.size, mission.submissions.toList(),
            stored.detail.members.filter { it.id !in done }.map { it.displayed }, mission.template, mission.spec)
    }

    // 달력에서 연 지난 미션: 미션 정보는 달력 기록, 제출물은 pastFeeds에서 가져와요.
    private fun pastResult(stored: StoredGroup, missionID: Long): MissionResult? {
        val record = calendarRecord(stored.id, missionID) ?: return null
        val submissions = pastFeeds[missionID] ?: return null
        val done = submissions.mapNotNull { it.authorID }.toSet()
        // 틀은 미션 정보가 아직 없어도 제출물에서 알 수 있어요(한 미션의 제출은 모두 같은 틀).
        val (template, spec) = pastSpecs[missionID] ?: ((submissions.firstOrNull()?.template ?: "") to MissionSpec())
        return MissionResult(missionID, stored.detail.name, record.type, record.topic, record.date,
            record.memberCount, submissions, stored.detail.members.filter { it.id !in done }.map { it.displayed },
            template, spec)
    }

    private fun update(groupID: Long, change: (StoredGroup) -> StoredGroup) {
        val index = _groups.indexOfFirst { it.id == groupID }
        if (index < 0) return
        val old = _groups[index]
        val next = change(old)
        if (next != old) _groups[index] = next
    }

    internal fun replaceMissionsForTest(groupID: Long, missions: List<StoredMission>) = update(groupID) {
        it.copy(detail = it.detail.copy(missionCount = missions.size), missions = missions.toList())
    }

    internal fun makeInviteCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        while (true) {
            val code = (1..6).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
            if (groups.none { it.detail.inviteCode == code } && DemoSeed.group(code, Instant.now(clock)) == null) return code
        }
    }

    companion object {
        fun preview(mediaDir: File, submitted: Boolean = true): DataStore {
            val store = DataStore(mediaDir)
            val id = store.join("K7Q2PX") ?: return store
            if (submitted) store.replaceMissionsForTest(id, store.groups.first { it.id == id }.missions.map { mission ->
                if (mission.id == store.todayMission(id)?.id)
                    mission.copy(serverSubmittedByMe = true, feedLoaded = true) else mission
            })
            return store
        }
    }
}

internal object TopicPool {
    fun topics(type: MissionType): List<String> = when (type) {
        MissionType.DRAW -> listOf("요즘 제일 웃겼던 순간", "오늘 기분을 날씨로 그려줘", "내 방 책상 위", "지금 제일 먹고 싶은 것", "서로의 첫인상")
        MissionType.PHOTO -> listOf("지금 창밖으로 보이는 풍경", "오늘 먹은 것 중 최고", "지금 신고 있는 신발", "오늘의 하늘", "내 주변에서 가장 파란 것")
        MissionType.WRITE -> listOf("오늘 들은 가장 따뜻한 말", "너에게 해주고 싶은 말 한 줄", "요즘 빠져 있는 것", "오늘 하루를 세 단어로", "지금 가장 고마운 사람")
    }

    /** 목업 미션에 붙이는 대표 틀(서버 미션은 늘 실제 틀을 가져와요). */
    fun template(type: MissionType): String = when (type) {
        MissionType.DRAW -> MissionTemplates.DRAW_SKETCH
        MissionType.PHOTO -> MissionTemplates.PHOTO_NOW
        MissionType.WRITE -> MissionTemplates.TEXT_FILL_BLANK
    }
}
