package com.sapphire.hawe.model

import android.net.Uri
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** 미션·캘린더 기준 시간대 (CLAUDE.md §5-1) */
val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

fun newId(): String = UUID.randomUUID().toString()

/** 그룹 멤버. 화면에 보일 때는 `그룹 내 값 ?: 기본 값` (CLAUDE.md §5-4) */
data class GroupMember(
    val id: String,
    val defaultProfile: Profile,
    /** 그룹 내 프로필 — null 이면 기본 프로필 */
    val groupProfile: Profile? = null,
    val isMe: Boolean = false,
    val isOwner: Boolean = false,
) {
    val displayed: Profile get() = groupProfile ?: defaultProfile
}

/** 16 그룹 정보 · 설정에 필요한 정보 */
data class GroupDetail(
    val id: String,
    val name: String,
    val relationship: Relationship,
    val createdAt: LocalDate,
    val missionCount: Int,
    val inviteCode: String,
    /** 주제 도착 시간 (Asia/Seoul, 기본 오전 9:00) */
    val topicTime: LocalTime,
    /** 이 그룹 알림 (group_members.notify) */
    val notify: Boolean,
    val members: List<GroupMember>,
    /** 연인 그룹의 '우리의 1일' */
    val startDate: LocalDate? = null,
) {
    val me: GroupMember? get() = members.firstOrNull { it.isMe }
    val amIOwner: Boolean get() = me?.isOwner ?: false
}

/** 04 홈 그룹 카드 */
data class GroupSummary(
    val id: String,
    val name: String,
    val relationship: Relationship,
    val members: List<AvatarContent>,
    val startDate: LocalDate?,
    val missionType: MissionType,
    val topic: String,
    val submittedCount: Int,
    val didSubmit: Boolean,
) {
    /** 우리의 1일을 1일째로 세요. */
    fun daysTogether(today: LocalDate = LocalDate.now(SEOUL)): Long? =
        startDate?.let { java.time.temporal.ChronoUnit.DAYS.between(it, today) + 1 }
}

/** 작품에 달린 댓글 (DB `comments`) */
data class SubmissionComment(
    val id: String,
    val author: Profile,
    val isMine: Boolean,
    val body: String,
    val createdAt: Instant,
)

/** 한 멤버가 올린 작품 (DB `submissions`) */
data class Submission(
    val id: String,
    val authorId: String,
    val author: Profile,
    val isMine: Boolean,
    val type: MissionType,
    val createdAt: Instant,
    /** 그림·사진·음성 파일 (서버 연결 전에는 기기 안 파일) */
    val media: Uri? = null,
    val text: String? = null,
    val durationSec: Double? = null,
    /** 음성 파형 막대 높이 0..1 */
    val waveform: List<Float> = emptyList(),
    val likeCount: Int = 0,
    val likedByMe: Boolean = false,
    val likerNames: List<String> = emptyList(),
    val comments: List<SubmissionComment> = emptyList(),
)

/** 하루치 미션과 제출물 */
data class StoredMission(
    val id: String,
    val type: MissionType,
    val topic: String,
    val number: Int,
    val opensAt: Instant,
    val closesAt: Instant,
    val submissions: List<Submission> = emptyList(),
    /** 오늘 내가 콕 찌른 멤버 */
    val poked: Set<String> = emptySet(),
)

/** 그룹 하나와 그 그룹의 미션 기록 (오래된 순) */
data class StoredGroup(val detail: GroupDetail, val missions: List<StoredMission> = emptyList()) {
    val id: String get() = detail.id
}

/** 오늘의 미션 참여 현황 한 줄 */
data class MissionMember(
    val id: String,
    val profile: Profile,
    val isMe: Boolean,
    val isDone: Boolean,
    val pokedToday: Boolean,
)

/** 05 그룹 · 오늘의 미션 */
data class TodayMission(
    val id: String,
    val group: GroupDetail,
    val type: MissionType,
    val topic: String,
    val number: Int,
    val opensAt: Instant,
    val closesAt: Instant,
    val members: List<MissionMember>,
) {
    val doneCount: Int get() = members.count { it.isDone }
    val didSubmit: Boolean get() = members.firstOrNull { it.isMe }?.isDone ?: false
}

/** 하루치 미션 결과 (10~13) */
data class MissionResult(
    val id: String,
    val groupName: String,
    val type: MissionType,
    val topic: String,
    val date: Instant,
    val memberCount: Int,
    val submissions: List<Submission>,
    /** 아직 안 올린 멤버 — '아직' 상태로 보여요 (CLAUDE.md §5-2) */
    val pending: List<Profile>,
)

/** 15 캘린더 하루 */
data class DayRecord(
    val date: LocalDate,
    val type: MissionType,
    val topic: String,
    val doneCount: Int,
    val memberCount: Int,
    /** 내가 그날 올렸는지 — 안 올린 날은 결과를 볼 수 없어요 */
    val didSubmit: Boolean,
    val missionId: String,
)

/** 17 마이페이지 활동 요약 */
data class ActivityStats(
    val completedMissions: Int,
    val streakDays: Int,
    val groupCount: Int,
    val byType: Map<MissionType, Int>,
)

/** 03 초대 코드로 찾은 그룹 */
data class GroupInvitePreview(val name: String, val relationship: Relationship, val members: List<AvatarContent>) {
    val isFull: Boolean get() = members.size >= relationship.maxMembers
}

/** 미션 수행 화면에서 올리는 내용 */
sealed interface SubmissionContent {
    /** 그림(PNG)·사진(JPEG) 파일 */
    data class Image(val file: Uri) : SubmissionContent
    data class Text(val text: String) : SubmissionContent
    data class Voice(val file: Uri, val durationSec: Double, val waveform: List<Float>) : SubmissionContent
}
