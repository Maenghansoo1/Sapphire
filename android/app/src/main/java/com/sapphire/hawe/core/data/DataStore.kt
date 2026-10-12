package com.sapphire.hawe.core.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sapphire.hawe.core.HaweTime
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
import kotlin.random.Random

sealed interface SubmissionContent {
    data class Image(val bytes: ByteArray, val fileExtension: String) : SubmissionContent
    data class Text(val body: String) : SubmissionContent
    data class Voice(val file: File, val duration: Double, val waveform: List<Float>) : SubmissionContent
}

enum class InviteLookupError(val message: String) {
    NOT_FOUND("초대 코드를 찾을 수 없어요. 다시 확인해 주세요."),
    FULL("이 그룹은 정원이 다 찼어요."),
    ALREADY_MEMBER("이미 참여 중인 그룹이에요."),
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
    val summary: MissionSummary? = null, val nextPublishAt: Instant? = null,
    val missionState: MissionState = MissionState.NOT_LOADED) {
    val id: Long get() = detail.id
}

data class StoredMission(
    val id: Long, val type: MissionType?, val topic: String, val number: Int,
    val opensAt: Instant, val closesAt: Instant,
    val submissions: List<Submission> = emptyList(),
    val poked: Set<Long> = emptySet(),
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
        nextLocalGroupId = -1L; nextLocalMemberId = -1L
    }

    fun applyServerGroups(details: List<LoadedGroup>) {
        val old = _groups.associateBy { it.id }
        _groups.clear()
        _groups.addAll(details.map { loaded ->
            val previous = old[loaded.detail.id]
            StoredGroup(loaded.detail, previous?.missions.orEmpty(), loaded.mission,
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
        val row = state.mission
        val previous = group.missions.lastOrNull()?.takeIf { it.id == row?.id }
        val current = row?.let { StoredMission(it.id, it.type, it.topic, it.number,
            it.publishedAt, it.closesAt, previous?.submissions.orEmpty(), previous?.poked.orEmpty(),
            it.deadlineAt, it.template, it.spec, it.members + previous?.serverMembers.orEmpty().filterValues { done -> done },
            it.submittedByMe || previous?.serverSubmittedByMe == true, previous?.feedLoaded == true) }
        group.copy(missions = listOfNotNull(current), nextPublishAt = state.nextPublishAt,
            missionState = MissionState.LOADED)
    }

    fun markMissionFailed(groupID: Long) = update(groupID) {
        if (it.missions.isNotEmpty()) it else it.copy(missionState = MissionState.FAILED)
    }

    val stats: ActivityStats get() {
        val mine = groups.flatMap { group -> group.missions.mapNotNull { mission ->
            if (mission.serverSubmittedByMe) mission else null
        } }
        val days = mine.map { it.opensAt.atZone(HaweTime.SEOUL).toLocalDate() }.toSet()
        var day = Instant.now(clock).atZone(HaweTime.SEOUL).toLocalDate()
        if (day !in days) day = day.minusDays(1)
        var streak = 0
        while (day in days) { streak++; day = day.minusDays(1) }
        return ActivityStats(mine.size, streak, groups.size,
            mine.mapNotNull { it.type }.groupingBy { it }.eachCount())
    }

    fun group(id: Long): GroupDetail? = groups.firstOrNull { it.id == id }?.detail

    val summaries: List<GroupSummary> get() = groups.map { stored ->
        val detail = stored.detail
        val summary = stored.summary
        GroupSummary(detail.id, detail.name, detail.relationship, detail.members.map { it.displayed.avatar },
            detail.startDate, summary?.type, summary?.topic,
            summary?.submittedCount ?: 0, summary?.submittedByMe == true)
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
    fun setMyGroupProfile(profile: Profile?, groupID: Long) = update(groupID) { group ->
        group.copy(detail = group.detail.copy(members = group.detail.members.map {
            if (it.isMe) it.copy(groupProfile = profile) else it
        }))
    }

    fun todayMission(groupID: Long): TodayMission? {
        val stored = groups.firstOrNull { it.id == groupID } ?: return null
        val mission = stored.missions.lastOrNull() ?: return null
        return TodayMission(mission.id, stored.detail, mission.type, mission.topic, mission.number,
            mission.opensAt, mission.closesAt, stored.detail.members.map { member ->
                MissionMember(member.id, member.displayed, member.isMe,
                    mission.serverMembers[member.id] == true ||
                        (member.isMe && mission.serverSubmittedByMe))
            }, mission.deadlineAt, mission.template, mission.spec)
    }

    fun applyFeed(groupID: Long, missionID: Long, submissions: List<Submission>) = update(groupID) { group ->
        val mission = group.missions.lastOrNull()?.takeIf { it.id == missionID } ?: return@update group
        group.copy(missions = listOf(mission.copy(submissions = submissions.toList(), feedLoaded = true)))
    }

    fun applySubmitted(groupID: Long, missionID: Long, submission: Submission) = update(groupID) { group ->
        val mission = group.missions.lastOrNull()?.takeIf { it.id == missionID } ?: return@update group
        val myId = group.detail.me?.id
        val updated = mission.copy(serverSubmittedByMe = true,
            serverMembers = if (myId == null) mission.serverMembers else mission.serverMembers + (myId to true),
            submissions = if (mission.submissions.any { it.id == submission.id }) mission.submissions
                else mission.submissions + submission)
        val summary = group.summary?.takeIf { it.missionId == missionID }?.let {
            it.copy(submittedCount = it.submittedCount + if (it.submittedByMe) 0 else 1, submittedByMe = true)
        } ?: group.summary
        group.copy(missions = listOf(updated), summary = summary)
    }

    fun poke(memberID: Long, groupID: Long) = update(groupID) { group ->
        val mission = group.missions.lastOrNull() ?: return@update group
        group.copy(missions = group.missions.dropLast(1) + mission.copy(poked = mission.poked + memberID))
    }

    fun result(groupID: Long, missionID: Long): MissionResult? {
        val stored = groups.firstOrNull { it.id == groupID } ?: return null
        val mission = stored.missions.firstOrNull { it.id == missionID } ?: return null
        if (!mission.feedLoaded) return null
        val done = mission.submissions.mapNotNull { it.authorID }.toSet()
        return MissionResult(mission.id, stored.detail.name, mission.type ?: return null, mission.topic, mission.opensAt,
            stored.detail.members.size, mission.submissions.toList(),
            stored.detail.members.filter { it.id !in done }.map { it.displayed })
    }

    fun records(groupID: Long): List<DayRecord> {
        val stored = groups.firstOrNull { it.id == groupID } ?: return emptyList()
        val today = Instant.now(clock).atZone(HaweTime.SEOUL).toLocalDate()
        return stored.missions.mapNotNull { mission ->
            val day = mission.opensAt.atZone(HaweTime.SEOUL).toLocalDate()
            val mine = mission.serverSubmittedByMe
            if (mission.type == null || mission.submissions.isEmpty() || (day >= today && !mine)) null else
                DayRecord(mission.opensAt, mission.type, mission.topic, mission.submissions.size,
                    stored.detail.members.size, mine, mission.id)
        }
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
        MissionType.VOICE -> listOf("오늘 하루를 10초로 말해줘", "요즘 흥얼거리는 노래 한 소절", "지금 들리는 소리", "내일의 나에게 한마디", "아침 인사 한마디")
    }
}
