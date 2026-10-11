package com.sapphire.hawe.feature.results

import com.sapphire.hawe.core.HaweTime
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.Submission
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.math.floor

fun koreanTimeText(instant: Instant): String {
    val time = instant.atZone(HaweTime.SEOUL)
    val hour = time.hour % 12
    return "${if (time.hour < 12) "오전" else "오후"} ${if (hour == 0) 12 else hour}:${time.minute.toString().padStart(2, '0')}"
}

fun koreanMonthDayText(instant: Instant): String {
    val date = instant.atZone(HaweTime.SEOUL)
    return "${date.monthValue}월 ${date.dayOfMonth}일"
}

fun koreanRelativeText(createdAt: Instant, now: Instant = Instant.now()): String {
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

fun shortDurationText(seconds: Double): String {
    val total = floor(seconds.coerceAtLeast(0.0)).toInt()
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

fun progressText(done: Int, memberCount: Int): String =
    if (done == memberCount) "${done}명 모두 완료" else "$done / ${memberCount}명 완료"

fun hintText(type: MissionType): String = if (type == MissionType.VOICE)
    "눌러서 듣고 댓글을 남겨보세요" else "${type.displayName}을 눌러 댓글을 남겨보세요"

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

fun voiceProgress(elapsed: Double, duration: Double): Float =
    (elapsed / maxOf(duration, 0.1)).coerceIn(0.0, 1.0).toFloat()
