package com.sapphire.hawe.feature.mission

import com.sapphire.hawe.core.HaweTime
import com.sapphire.hawe.core.model.MissionMember
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.PokeKind
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val missionDateFormat = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)

fun missionDateText(opensAt: Instant): String = missionDateFormat.format(opensAt.atZone(HaweTime.SEOUL))

fun countdownText(closesAt: Instant, now: Instant): String {
    val seconds = Duration.between(now, closesAt).toMillis().coerceAtLeast(0) / 1000
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    val remainingSeconds = seconds % 60
    return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}"
}

fun deadlineText(deadlineAt: Instant, closesAt: Instant, now: Instant): String? {
    if (!now.isBefore(closesAt)) return null
    if (!now.isBefore(deadlineAt)) return "마감이 지났어요 · 지금 올리면 지각이에요"
    val deadline = deadlineAt.atZone(HaweTime.SEOUL)
    val today = now.atZone(HaweTime.SEOUL).toLocalDate()
    val date = when (deadline.toLocalDate()) {
        today -> ""
        today.plusDays(1) -> "내일 "
        else -> "${deadline.monthValue}월 ${deadline.dayOfMonth}일 "
    }
    val hour = (deadline.hour % 12).let { if (it == 0) 12 else it }
    return "마감 $date${if (deadline.hour < 12) "오전" else "오후"} $hour:${deadline.minute.toString().padStart(2, '0')}"
}

fun memberDisplayName(member: MissionMember): String =
    if (member.isMe) "${member.profile.nickname} (나)" else member.profile.nickname

fun lockNoticeText(type: MissionType): String = "내 미션을 올리면 친구들의 ${type.subjectForm} 열려요."
fun pokeToastText(nickname: String): String = "${nickname}님을 콕 찔렀어요"
fun flingToastText(nickname: String): String = "${nickname}님에게 똥을 뿌렸어요"
fun pokeSentText(kind: PokeKind, nickname: String): String = when (kind) {
    PokeKind.POKE -> pokeToastText(nickname)
    PokeKind.FLING -> flingToastText(nickname)
}
fun pokeButtonLabel(kind: PokeKind): String = when (kind) {
    PokeKind.POKE -> "콕"
    PokeKind.FLING -> "똥"
}
/** 화면 읽기(TalkBack)용. 보낸 뒤에는 보낸 사실을 읽어 준다. */
fun pokeButtonDescription(kind: PokeKind, nickname: String, sent: Boolean): String = if (sent) pokeSentText(kind, nickname)
    else when (kind) {
        PokeKind.POKE -> "${nickname}님 콕 찌르기"
        PokeKind.FLING -> "${nickname}님에게 똥 뿌리기"
    }
