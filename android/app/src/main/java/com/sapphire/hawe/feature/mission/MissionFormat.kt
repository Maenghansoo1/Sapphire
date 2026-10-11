package com.sapphire.hawe.feature.mission

import com.sapphire.hawe.core.HaweTime
import com.sapphire.hawe.core.model.MissionMember
import com.sapphire.hawe.core.model.MissionType
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

fun memberDisplayName(member: MissionMember): String =
    if (member.isMe) "${member.profile.nickname} (나)" else member.profile.nickname

fun lockNoticeText(type: MissionType): String = "내 미션을 올리면 친구들의 ${type.subjectForm} 열려요."
fun pokeToastText(nickname: String): String = "${nickname}님을 콕 찔렀어요"
