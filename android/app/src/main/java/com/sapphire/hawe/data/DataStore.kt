package com.sapphire.hawe.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sapphire.hawe.model.ActivityStats
import com.sapphire.hawe.model.DayRecord
import com.sapphire.hawe.model.GroupDetail
import com.sapphire.hawe.model.GroupInvitePreview
import com.sapphire.hawe.model.GroupMember
import com.sapphire.hawe.model.GroupSummary
import com.sapphire.hawe.model.MissionMember
import com.sapphire.hawe.model.MissionResult
import com.sapphire.hawe.model.MissionType
import com.sapphire.hawe.model.Profile
import com.sapphire.hawe.model.Relationship
import com.sapphire.hawe.model.SEOUL
import com.sapphire.hawe.model.StoredGroup
import com.sapphire.hawe.model.StoredMission
import com.sapphire.hawe.model.Submission
import com.sapphire.hawe.model.SubmissionComment
import com.sapphire.hawe.model.SubmissionContent
import com.sapphire.hawe.model.TodayMission
import com.sapphire.hawe.model.newId
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** 초대 코드 조회 실패 */
enum class InviteLookupError(val message: String) {
    NotFound("초대 코드를 찾을 수 없어요. 다시 확인해 주세요."),
    Full("이 그룹은 정원이 다 찼어요."),
    AlreadyMember("이미 참여 중인 그룹이에요."),
}

/**
 * 앱 안 임시 저장소 — 서버를 붙이기 전까지 모든 화면이 여기서 데이터를 읽고 써요 (iOS DataStore 와 같은 규칙).
 * 앱을 끄면 사라져요. Spring 서버를 붙일 때는 이 클래스의 공개 함수 안쪽만 API 호출로 바꾸면 돼요.
 */
class DataStore {
    var groups by mutableStateOf<List<StoredGroup>>(emptyList())
        private set

    /** 내 기본 프로필 (마이페이지) */
    var myProfile by mutableStateOf(Profile.MockMine)
        private set

    // MARK: - 내 정보

    fun updateMyProfile(profile: Profile) {
        myProfile = profile
        groups = groups.map { g ->
            g.copy(detail = g.detail.copy(members = g.detail.members.map { if (it.isMe) it.copy(defaultProfile = profile) else it }))
        }
    }

    /** 회원 탈퇴 — 모두 지워요 (CLAUDE.md §5-5) */
    fun reset() {
        groups = emptyList()
        myProfile = Profile.MockMine
    }

    /** 17 마이페이지 활동 요약 — 실제로 올린 기록으로 계산 */
    val stats: ActivityStats
        get() {
            val mine = groups.flatMap { g -> g.missions.filter { m -> m.submissions.any { it.isMine } } }
            val days = mine.map { it.opensAt.atZone(SEOUL).toLocalDate() }.toSet()
            var day = LocalDate.now(SEOUL)
            if (day !in days) day = day.minusDays(1)
            var streak = 0
            while (day in days) {
                streak++
                day = day.minusDays(1)
            }
            return ActivityStats(mine.size, streak, groups.size, mine.groupingBy { it.type }.eachCount())
        }

    // MARK: - 그룹

    fun group(id: String): GroupDetail? = groups.firstOrNull { it.id == id }?.detail

    /** 04 홈 카드 */
    val summaries: List<GroupSummary>
        get() = groups.map { g ->
            val mission = g.missions.lastOrNull()
            GroupSummary(
                id = g.id,
                name = g.detail.name,
                relationship = g.detail.relationship,
                members = g.detail.members.map { it.displayed.avatar },
                startDate = g.detail.startDate,
                missionType = mission?.type ?: MissionType.Draw,
                topic = mission?.topic ?: "",
                submittedCount = mission?.submissions?.size ?: 0,
                didSubmit = mission?.submissions?.any { it.isMine } ?: false,
            )
        }

    /** 02 그룹 만들기 — 나 혼자, 방장. 오늘 미션도 바로 만들어요. */
    fun createGroup(name: String, relationship: Relationship, startDate: LocalDate?): String {
        val detail = GroupDetail(
            id = newId(),
            name = name,
            relationship = relationship,
            createdAt = LocalDate.now(SEOUL),
            missionCount = 0,
            inviteCode = makeInviteCode(),
            topicTime = LocalTime.of(9, 0),
            notify = true,
            members = listOf(GroupMember(newId(), myProfile, isMe = true, isOwner = true)),
            startDate = if (relationship == Relationship.Lover) startDate else null,
        )
        groups = groups + StoredGroup(detail)
        refreshTodayMissions()
        return detail.id
    }

    /** 03 초대 코드 조회 — 내 그룹 코드는 '이미 참여 중', 체험용 그룹은 DemoSeed 에 있어요. */
    fun lookUpInvite(code: String): Result<GroupInvitePreview> {
        if (groups.any { it.detail.inviteCode == code }) return Result.failure(InviteError(InviteLookupError.AlreadyMember))
        val demo = DemoSeed.group(code) ?: return Result.failure(InviteError(InviteLookupError.NotFound))
        val preview = GroupInvitePreview(demo.detail.name, demo.detail.relationship, demo.detail.members.map { it.displayed.avatar })
        return if (preview.isFull) Result.failure(InviteError(InviteLookupError.Full)) else Result.success(preview)
    }

    /** 03 참여하기 */
    fun join(code: String): String? {
        if (lookUpInvite(code).isFailure) return null
        val demo = DemoSeed.group(code) ?: return null
        val joined = demo.copy(detail = demo.detail.copy(members = demo.detail.members + GroupMember(newId(), myProfile, isMe = true)))
        groups = groups + joined
        refreshTodayMissions()
        return joined.id
    }

    /** 16 그룹 나가기 — 내 작품·댓글은 남아요(사용자 결정). 방장 넘기기는 서버가 해요(§5-3). */
    fun leave(groupId: String) {
        groups = groups.filterNot { it.id == groupId }
    }

    fun setTopicTime(time: LocalTime, groupId: String) = update(groupId) { it.copy(detail = it.detail.copy(topicTime = time)) }

    fun setNotify(on: Boolean, groupId: String) = update(groupId) { it.copy(detail = it.detail.copy(notify = on)) }

    /** 16-1 그룹 내 프로필 (null 이면 기본 프로필) */
    fun setMyGroupProfile(profile: Profile?, groupId: String) = update(groupId) { g ->
        g.copy(detail = g.detail.copy(members = g.detail.members.map { if (it.isMe) it.copy(groupProfile = profile) else it }))
    }

    // MARK: - 미션

    /** 그룹마다 지금 열려 있는 미션이 없으면 만들어요 (CLAUDE.md §5-1 — 원래는 서버 스케줄이 해요). */
    fun refreshTodayMissions(now: Instant = Instant.now()) {
        groups = groups.map { g ->
            val (opens, closes) = missionWindow(g.detail.topicTime, now)
            val last = g.missions.lastOrNull()
            if (last != null && !last.opensAt.isBefore(opens)) g
            else {
                val missions = g.missions + makeMission(g, opens, closes)
                g.copy(detail = g.detail.copy(missionCount = missions.size), missions = missions)
            }
        }
    }

    /** 05 화면용 오늘 미션 */
    fun todayMission(groupId: String): TodayMission? {
        val g = groups.firstOrNull { it.id == groupId } ?: return null
        val m = g.missions.lastOrNull() ?: return null
        return TodayMission(
            id = m.id,
            group = g.detail,
            type = m.type,
            topic = m.topic,
            number = m.number,
            opensAt = m.opensAt,
            closesAt = m.closesAt,
            members = g.detail.members.map { member ->
                MissionMember(member.id, member.displayed, member.isMe, m.submissions.any { it.authorId == member.id }, member.id in m.poked)
            },
        )
    }

    /** 06~09 올리기 — 한 번 올리면 수정·삭제할 수 없어요 (§5-2) */
    fun submit(content: SubmissionContent, groupId: String) = update(groupId) { g ->
        val me = g.detail.me ?: return@update g
        val last = g.missions.lastOrNull() ?: return@update g
        if (last.submissions.any { it.isMine }) return@update g
        var submission = Submission(newId(), me.id, me.displayed, isMine = true, type = last.type, createdAt = Instant.now())
        submission = when (content) {
            is SubmissionContent.Image -> submission.copy(media = content.file)
            is SubmissionContent.Text -> submission.copy(text = content.text)
            is SubmissionContent.Voice -> submission.copy(media = content.file, durationSec = content.durationSec, waveform = content.waveform)
        }
        g.copy(missions = g.missions.dropLast(1) + last.copy(submissions = last.submissions + submission))
    }

    /** 콕 찌르기 — 하루 1회 (TODO: 서버 연결 시 pokes 저장 + 푸시) */
    fun poke(memberId: String, groupId: String) = update(groupId) { g ->
        val last = g.missions.lastOrNull() ?: return@update g
        g.copy(missions = g.missions.dropLast(1) + last.copy(poked = last.poked + memberId))
    }

    // MARK: - 결과 · 댓글

    /** 10~13 결과 — 내가 올린 미션만 볼 수 있어요 (§5-2, 서버에서는 RLS 로 막아요) */
    fun result(groupId: String, missionId: String): MissionResult? {
        val g = groups.firstOrNull { it.id == groupId } ?: return null
        val m = g.missions.firstOrNull { it.id == missionId } ?: return null
        if (m.submissions.none { it.isMine }) return null
        val done = m.submissions.map { it.authorId }.toSet()
        return MissionResult(
            m.id, g.detail.name, m.type, m.topic, m.opensAt, g.detail.members.size, m.submissions,
            g.detail.members.filter { it.id !in done }.map { it.displayed },
        )
    }

    fun toggleLike(submissionId: String, missionId: String, groupId: String) = updateSubmission(submissionId, missionId, groupId) {
        val liked = !it.likedByMe
        it.copy(likedByMe = liked, likeCount = it.likeCount + if (liked) 1 else -1)
    }

    fun addComment(body: String, submissionId: String, missionId: String, groupId: String) {
        val me = group(groupId)?.me?.displayed ?: myProfile
        updateSubmission(submissionId, missionId, groupId) {
            it.copy(comments = it.comments + SubmissionComment(newId(), me, true, body, Instant.now()))
        }
    }

    fun deleteComment(commentId: String, submissionId: String, missionId: String, groupId: String) =
        updateSubmission(submissionId, missionId, groupId) { s -> s.copy(comments = s.comments.filterNot { it.id == commentId && it.isMine }) }

    // MARK: - 기록

    /** 15 캘린더 — 지난 미션 중 누군가 올린 날 + 오늘(내가 올렸으면) */
    fun records(groupId: String): List<DayRecord> {
        val g = groups.firstOrNull { it.id == groupId } ?: return emptyList()
        val today = LocalDate.now(SEOUL)
        return g.missions.mapNotNull { m ->
            val day = m.opensAt.atZone(SEOUL).toLocalDate()
            val mine = m.submissions.any { it.isMine }
            if (m.submissions.isEmpty() || !(day.isBefore(today) || mine)) null
            else DayRecord(day, m.type, m.topic, m.submissions.size, g.detail.members.size, mine, m.id)
        }
    }

    // MARK: - 내부

    private fun update(groupId: String, change: (StoredGroup) -> StoredGroup) {
        groups = groups.map { if (it.id == groupId) change(it) else it }
    }

    private fun updateSubmission(id: String, missionId: String, groupId: String, change: (Submission) -> Submission) = update(groupId) { g ->
        g.copy(missions = g.missions.map { m ->
            if (m.id != missionId) m else m.copy(submissions = m.submissions.map { if (it.id == id) change(it) else it })
        })
    }

    /** 6자리 영문 대문자 + 숫자, 그룹마다 고유 (§5-3). 헷갈리는 0·O·1·I 는 빼요. */
    private fun makeInviteCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        while (true) {
            val code = (1..6).map { alphabet.random() }.joinToString("")
            if (groups.none { it.detail.inviteCode == code } && DemoSeed.group(code) == null) return code
        }
    }

    /** 유형은 3일 연속 같지 않게, 주제는 최근 30일 안에 쓴 것 빼고 (§5-1) */
    private fun makeMission(g: StoredGroup, opens: Instant, closes: Instant): StoredMission {
        val recent = g.missions.takeLast(2).map { it.type }
        val types = MissionType.entries.filterNot { recent.size == 2 && recent[0] == recent[1] && it == recent[0] }
        val type = types.random()
        val used = g.missions.filter { Duration.between(it.opensAt, opens).toDays() < 30 }.map { it.topic }.toSet()
        val pool = TopicPool.topics(type)
        val topic = pool.filterNot { it in used }.randomOrNull() ?: pool.random()
        return StoredMission(newId(), type, topic, g.missions.size + 1, opens, closes)
    }

    private class InviteError(val reason: InviteLookupError) : Exception(reason.message)

    companion object {
        /** 주제 도착 시간 기준으로 지금 열려 있는 미션의 시작·끝 (CLAUDE.md §5-1) */
        fun missionWindow(topicTime: LocalTime, now: Instant = Instant.now()): Pair<Instant, Instant> {
            val zNow = now.atZone(SEOUL)
            var open: ZonedDateTime = zNow.toLocalDate().atTime(topicTime).atZone(SEOUL)
            if (zNow.isBefore(open)) open = open.minusDays(1)
            return open.toInstant() to open.plusDays(1).toInstant()
        }

        /** 조회 실패 이유 꺼내기 */
        fun inviteError(result: Result<GroupInvitePreview>): InviteLookupError? = (result.exceptionOrNull() as? InviteError)?.reason
    }
}

/** 유형별 주제 풀 (CLAUDE.md §6 topics — 원래는 서버 DB 에 있어요) */
object TopicPool {
    fun topics(type: MissionType): List<String> = when (type) {
        MissionType.Draw -> listOf("요즘 제일 웃겼던 순간", "오늘 기분을 날씨로 그려줘", "내 방 책상 위", "지금 제일 먹고 싶은 것", "서로의 첫인상")
        MissionType.Photo -> listOf("지금 창밖으로 보이는 풍경", "오늘 먹은 것 중 최고", "지금 신고 있는 신발", "오늘의 하늘", "내 주변에서 가장 파란 것")
        MissionType.Write -> listOf("오늘 들은 가장 따뜻한 말", "너에게 해주고 싶은 말 한 줄", "요즘 빠져 있는 것", "오늘 하루를 세 단어로", "지금 가장 고마운 사람")
        MissionType.Voice -> listOf("오늘 하루를 10초로 말해줘", "요즘 흥얼거리는 노래 한 소절", "지금 들리는 소리", "내일의 나에게 한마디", "아침 인사 한마디")
    }
}
