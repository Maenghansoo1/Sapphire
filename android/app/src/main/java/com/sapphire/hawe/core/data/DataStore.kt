package com.sapphire.hawe.core.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sapphire.hawe.core.HaweTime
import com.sapphire.hawe.core.model.*
import com.sapphire.hawe.designsystem.assets.Relationship
import java.io.File
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID
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

sealed interface InviteLookupResult {
    data class Success(val preview: GroupInvitePreview) : InviteLookupResult
    data class Failure(val error: InviteLookupError) : InviteLookupResult
}

data class StoredGroup(val detail: GroupDetail, val missions: List<StoredMission> = emptyList()) {
    val id: Long get() = detail.id
}

data class StoredMission(
    val id: UUID, val type: MissionType, val topic: String, val number: Int,
    val opensAt: Instant, val closesAt: Instant,
    val submissions: List<Submission> = emptyList(),
    val poked: Set<Long> = emptySet(),
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

    // Local missions remain attached to server groups until the mission API is connected.
    fun applyServerGroups(details: List<GroupDetail>) {
        val old = _groups.associateBy { it.id }
        _groups.clear()
        _groups.addAll(details.map { detail ->
            val missions = old[detail.id]?.missions.orEmpty()
            StoredGroup(detail.copy(missionCount = if (missions.isEmpty()) detail.missionCount else missions.size), missions)
        })
        groupsState = GroupsState.LOADED
    }

    fun applyServerGroup(detail: GroupDetail) {
        val index = _groups.indexOfFirst { it.id == detail.id }
        val missions = if (index < 0) emptyList() else _groups[index].missions
        val stored = StoredGroup(detail.copy(missionCount = if (missions.isEmpty()) detail.missionCount else missions.size), missions)
        if (index < 0) _groups.add(stored) else _groups[index] = stored
    }

    fun markGroupsFailed() {
        if (groupsState == GroupsState.NOT_LOADED) groupsState = GroupsState.FAILED
    }

    val stats: ActivityStats get() {
        val mine = groups.flatMap { group -> group.missions.mapNotNull { mission ->
            if (mission.submissions.any { it.isMine }) mission else null
        } }
        val days = mine.map { it.opensAt.atZone(HaweTime.SEOUL).toLocalDate() }.toSet()
        var day = Instant.now(clock).atZone(HaweTime.SEOUL).toLocalDate()
        if (day !in days) day = day.minusDays(1)
        var streak = 0
        while (day in days) { streak++; day = day.minusDays(1) }
        return ActivityStats(mine.size, streak, groups.size, mine.groupingBy { it.type }.eachCount())
    }

    fun group(id: Long): GroupDetail? = groups.firstOrNull { it.id == id }?.detail

    val summaries: List<GroupSummary> get() = groups.map { stored ->
        val detail = stored.detail
        val mission = stored.missions.lastOrNull()
        GroupSummary(detail.id, detail.name, detail.relationship, detail.members.map { it.displayed.avatar },
            detail.startDate, mission?.type ?: MissionType.DRAW, mission?.topic ?: "",
            mission?.submissions?.size ?: 0, mission?.submissions?.any { it.isMine } == true)
    }

    // Local simulation for tests and previews only. Production uses GroupRepository.
    internal fun createGroup(name: String, relationship: Relationship, startDate: LocalDate? = null): Long {
        val detail = GroupDetail(nextLocalId(), name, relationship, Instant.now(clock), 0,
            makeInviteCode(), members = listOf(GroupMember(nextLocalMember(), myProfile, isMe = true, isOwner = true)),
            startDate = if (relationship == Relationship.LOVER) startDate else null)
        _groups.add(StoredGroup(detail))
        refreshTodayMissions()
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
        refreshTodayMissions()
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

    fun refreshTodayMissions(now: Instant = Instant.now(clock)) {
        _groups.indices.forEach { index ->
            val stored = _groups[index]
            val (opens, closes) = HaweTime.window(stored.detail.topicTime, now)
            if (stored.missions.lastOrNull()?.opensAt?.let { it >= opens } == true) return@forEach
            val missions = stored.missions + makeMission(stored, opens, closes)
            _groups[index] = stored.copy(detail = stored.detail.copy(missionCount = missions.size), missions = missions)
        }
    }

    fun todayMission(groupID: Long): TodayMission? {
        val stored = groups.firstOrNull { it.id == groupID } ?: return null
        val mission = stored.missions.lastOrNull() ?: return null
        return TodayMission(mission.id, stored.detail, mission.type, mission.topic, mission.number,
            mission.opensAt, mission.closesAt, stored.detail.members.map { member ->
                MissionMember(member.id, member.displayed, member.isMe,
                    mission.submissions.any { it.authorID == member.id }, member.id in mission.poked)
            })
    }

    fun submit(content: SubmissionContent, groupID: Long) = update(groupID) { group ->
        val me = group.detail.me ?: return@update group
        val mission = group.missions.lastOrNull() ?: return@update group
        if (mission.submissions.any { it.isMine }) return@update group
        val base = Submission(UUID.randomUUID(), me.displayed, true, mission.type, Instant.now(clock), authorID = me.id)
        val submission = when (content) {
            is SubmissionContent.Image -> base.copy(mediaURL = saveMedia(content.bytes, content.fileExtension))
            is SubmissionContent.Text -> base.copy(text = content.body)
            is SubmissionContent.Voice -> base.copy(
                mediaURL = runCatching { saveMedia(content.file.readBytes(), "m4a") }.getOrNull(),
                duration = content.duration, waveform = content.waveform.toList())
        }
        group.copy(missions = group.missions.dropLast(1) + mission.copy(submissions = mission.submissions + submission))
    }

    fun poke(memberID: Long, groupID: Long) = update(groupID) { group ->
        val mission = group.missions.lastOrNull() ?: return@update group
        group.copy(missions = group.missions.dropLast(1) + mission.copy(poked = mission.poked + memberID))
    }

    fun result(groupID: Long, missionID: UUID): MissionResult? {
        val stored = groups.firstOrNull { it.id == groupID } ?: return null
        val mission = stored.missions.firstOrNull { it.id == missionID } ?: return null
        if (mission.submissions.none { it.isMine }) return null
        val done = mission.submissions.mapNotNull { it.authorID }.toSet()
        return MissionResult(mission.id, stored.detail.name, mission.type, mission.topic, mission.opensAt,
            stored.detail.members.size, mission.submissions.toList(),
            stored.detail.members.filter { it.id !in done }.map { it.displayed })
    }

    fun toggleLike(submissionID: UUID, missionID: UUID, groupID: Long) = updateSubmission(submissionID, missionID, groupID) {
        val liked = !it.likedByMe
        it.copy(likedByMe = liked, likeCount = it.likeCount + (if (liked) 1 else -1))
    }

    fun addComment(body: String, submissionID: UUID, missionID: UUID, groupID: Long) {
        val me = group(groupID)?.me?.displayed ?: myProfile
        updateSubmission(submissionID, missionID, groupID) {
            it.copy(comments = it.comments + SubmissionComment(UUID.randomUUID(), me, true, body, Instant.now(clock)))
        }
    }

    fun deleteComment(commentID: UUID, submissionID: UUID, missionID: UUID, groupID: Long) =
        updateSubmission(submissionID, missionID, groupID) {
            it.copy(comments = it.comments.filterNot { comment -> comment.id == commentID && comment.isMine })
        }

    fun records(groupID: Long): List<DayRecord> {
        val stored = groups.firstOrNull { it.id == groupID } ?: return emptyList()
        val today = Instant.now(clock).atZone(HaweTime.SEOUL).toLocalDate()
        return stored.missions.mapNotNull { mission ->
            val day = mission.opensAt.atZone(HaweTime.SEOUL).toLocalDate()
            val mine = mission.submissions.any { it.isMine }
            if (mission.submissions.isEmpty() || (day >= today && !mine)) null else
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

    private fun updateSubmission(id: UUID, missionID: UUID, groupID: Long, change: (Submission) -> Submission) = update(groupID) { group ->
        group.copy(missions = group.missions.map { mission ->
            if (mission.id != missionID) mission else mission.copy(submissions = mission.submissions.map { submission ->
                if (submission.id == id) change(submission) else submission
            })
        })
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

    private fun makeMission(group: StoredGroup, opens: Instant, closes: Instant): StoredMission {
        val recent = group.missions.takeLast(2).map { it.type }
        val types = MissionType.entries.filter { recent.size < 2 || recent[0] != recent[1] || it != recent[0] }
        val type = types.random(random)
        val used = group.missions.filter { ChronoUnit.SECONDS.between(it.opensAt, opens) < 30L * 86400 }
            .map { it.topic }.toSet()
        val pool = TopicPool.topics(type)
        val topic = (pool.filter { it !in used }.ifEmpty { pool }).random(random)
        return StoredMission(UUID.randomUUID(), type, topic, group.missions.size + 1, opens, closes)
    }

    private fun saveMedia(bytes: ByteArray, extension: String): URI? = runCatching {
        mediaDir.mkdirs()
        val safeExtension = extension.lowercase().filter { it in 'a'..'z' }.take(8).ifEmpty { "bin" }
        File(mediaDir, "${UUID.randomUUID()}.$safeExtension").apply { writeBytes(bytes) }.toURI()
    }.getOrNull()

    companion object {
        fun preview(mediaDir: File, submitted: Boolean = true): DataStore {
            val store = DataStore(mediaDir)
            val id = store.join("K7Q2PX") ?: return store
            if (submitted) {
                val type = store.todayMission(id)?.type
                if (type == MissionType.WRITE) store.submit(SubmissionContent.Text("미리보기에서 올린 글이에요."), id)
                else if (type == MissionType.VOICE) store.submit(SubmissionContent.Voice(File("/dev/null"), 8.0, List(28) { 0.5f }), id)
                else store.submit(SubmissionContent.Image(byteArrayOf(), "png"), id)
            }
            return store
        }
    }
}

object TopicPool {
    fun topics(type: MissionType): List<String> = when (type) {
        MissionType.DRAW -> listOf("요즘 제일 웃겼던 순간", "오늘 기분을 날씨로 그려줘", "내 방 책상 위", "지금 제일 먹고 싶은 것", "서로의 첫인상")
        MissionType.PHOTO -> listOf("지금 창밖으로 보이는 풍경", "오늘 먹은 것 중 최고", "지금 신고 있는 신발", "오늘의 하늘", "내 주변에서 가장 파란 것")
        MissionType.WRITE -> listOf("오늘 들은 가장 따뜻한 말", "너에게 해주고 싶은 말 한 줄", "요즘 빠져 있는 것", "오늘 하루를 세 단어로", "지금 가장 고마운 사람")
        MissionType.VOICE -> listOf("오늘 하루를 10초로 말해줘", "요즘 흥얼거리는 노래 한 소절", "지금 들리는 소리", "내일의 나에게 한마디", "아침 인사 한마디")
    }
}
