package com.sapphire.hawe.feature.results

import com.sapphire.hawe.core.HaweTime
import com.sapphire.hawe.core.dev.AppClock
import com.sapphire.hawe.core.model.MissionSpec
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.Submission
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

fun koreanTimeText(instant: Instant): String {
    val time = instant.atZone(HaweTime.SEOUL)
    val hour = time.hour % 12
    return "${if (time.hour < 12) "오전" else "오후"} ${if (hour == 0) 12 else hour}:${time.minute.toString().padStart(2, '0')}"
}

fun koreanMonthDayText(instant: Instant): String {
    val date = instant.atZone(HaweTime.SEOUL)
    return "${date.monthValue}월 ${date.dayOfMonth}일"
}

fun koreanRelativeText(createdAt: Instant, now: Instant = AppClock.now()): String {
    val seconds = Duration.between(createdAt, now).seconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "방금"
        seconds < 3600 -> "${seconds / 60}분 전"
        seconds < 86400 -> "${seconds / 3600}시간 전"
        seconds < 604800 -> "${seconds / 86400}일 전"
        seconds < 2419200 -> "${seconds / 604800}주 전"
        seconds < 31536000 -> "${(seconds / 2592000).coerceAtLeast(1)}개월 전"
        else -> "${seconds / 31536000}년 전"
    }
}

fun progressText(done: Int, memberCount: Int): String =
    if (done == memberCount) "${done}명 모두 완료" else "$done / ${memberCount}명 완료"

fun hintText(type: MissionType): String = "${type.objectForm} 눌러서 자세히 보기"

/** 별점 1~5 를 ★/☆ 다섯 칸으로(임시 표시 — 디자인 확정 시 교체). 범위 밖은 0~5 로 맞춰요. */
fun starsText(stars: Int): String {
    val filled = stars.coerceIn(0, 5)
    return "★".repeat(filled) + "☆".repeat(5 - filled)
}

/** 밸런스 A/B 비율(%). 합이 늘 100 이 되게 A 를 반올림하고 B 는 나머지예요. 아무도 없으면 0/0. */
fun balancePercents(countA: Int, countB: Int): Pair<Int, Int> {
    val total = countA + countB
    if (total <= 0) return 0 to 0
    val a = (countA * 100.0 / total).roundToInt()
    return a to 100 - a
}

/** "A · 짜장면"처럼 선택 글자와 서버 선택지. 선택지를 모르면 글자만. */
fun balanceChoiceText(choice: String?, spec: MissionSpec): String? {
    val option = when (choice) { "A" -> spec.optionA; "B" -> spec.optionB; else -> return null }
    return option?.takeIf { it.isNotBlank() }?.let { "$choice · $it" } ?: choice
}

fun likersText(names: List<String>, likeCount: Int): String? {
    if (names.isEmpty()) return null
    val shown = names.take(2).joinToString(", ")
    val others = likeCount - minOf(names.size, 2)
    return if (others > 0) "${shown}님 외 ${others}명이 좋아해요" else "${shown}님이 좋아해요"
}

fun commentSendEnabled(draft: String) = draft.trim().isNotEmpty()
fun commentPlaceholder(nickname: String) = "${nickname}님에게 댓글 남기기"

fun localImageFile(submission: Submission): File? {
    val uri = submission.mediaURL ?: return null
    if (uri.scheme != "file") return null
    return runCatching { File(uri).takeIf { it.isFile } }.getOrNull()
}

/** AI 심사 배지 이름(B10). 앱이 모르는 배지는 이름 없이 코멘트만 보여요. */
fun aiBadgeLabel(badge: String?): String? = if (badge == "PERFECT_FIT") "오늘의 주제 찰떡상" else null

/** 방금(2분 안) 올린 내 사진이 아직 AI 심사 전인지. 심사는 제출 뒤에 따로 끝나서 결과 화면이 한 번 더 불러와요. */
fun awaitsAiReview(submissions: List<Submission>, now: Instant): Boolean = submissions.any {
    it.isMine && it.type == MissionType.PHOTO && it.aiReview == null &&
        !it.createdAt.isAfter(now) && Duration.between(it.createdAt, now) < Duration.ofMinutes(2)
}
