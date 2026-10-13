package com.sapphire.hawe.feature.results

import com.sapphire.hawe.core.HaweTime
import com.sapphire.hawe.core.model.ConsensusRank
import com.sapphire.hawe.core.model.MissionSpec
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.Submission
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
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

/** 서버가 소수 첫째 자리로 반올림해 준 값을 그대로 보여요. 62.0 은 "62", 62.5 는 "62.5". */
fun decimalText(value: Double): String =
    if (value == Math.rint(value)) value.toLong().toString() else String.format(Locale.ROOT, "%.1f", value)

/** 내 위치와 평균의 차이(내 값 − 평균)를 양 끝 라벨로 풀어 말해요. */
fun diffFromAverageText(diff: Double?, spec: MissionSpec): String? {
    diff ?: return null
    if (diff == 0.0) return "내 위치가 딱 평균이에요"
    val side = if (diff > 0) spec.right else spec.left
    val direction = side?.takeIf { it.isNotBlank() }?.let { "‘$it’ 쪽으로" } ?: if (diff > 0) "오른쪽으로" else "왼쪽으로"
    return "내 위치는 평균보다 $direction ${decimalText(abs(diff))}"
}

/**
 * 다수결 예측 순위(같은 차이는 같은 등수: 1, 1, 3). 서버가 차이 작은 순으로 보내 줘요.
 * 그룹은 6명 이하라 서버가 반올림한 차이가 같으면 실제 차이도 같아요(평균 분모가 1~6).
 */
fun competitionRanks(ranking: List<ConsensusRank>): List<Int> =
    ranking.map { entry -> 1 + ranking.count { it.diff < entry.diff } }

/** 승자 안내. 동률이면 모두, 나는 "나"로 불러요. */
fun consensusWinnerText(ranking: List<ConsensusRank>, winnerUserIds: Set<Long>, myUserId: Long): String? {
    val winners = ranking.filter { it.userId in winnerUserIds }
    if (winners.isEmpty()) return null
    val mine = winners.any { it.userId == myUserId }
    val others = winners.filter { it.userId != myUserId }.joinToString(", ") { "${it.nickname}님" }
    return when {
        winners.size == 1 -> if (mine) "내가 가장 가깝게 맞혔어요" else "${others}이 가장 가깝게 맞혔어요"
        mine -> "나와 ${others}이 공동으로 가장 가깝게 맞혔어요"
        else -> "${others}이 공동으로 가장 가깝게 맞혔어요"
    }
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

