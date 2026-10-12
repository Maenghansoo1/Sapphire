package com.sapphire.hawe.feature.groups

import com.sapphire.hawe.core.HaweTime
import com.sapphire.hawe.core.model.GroupDetail
import com.sapphire.hawe.core.model.GroupMember
import com.sapphire.hawe.core.model.GroupSummary
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val korean = Locale.KOREAN
private val dateFormat = DateTimeFormatter.ofPattern("yyyy. M. d.", korean)
private val homeFormat = DateTimeFormatter.ofPattern("M월 d일 EEEE", korean)
fun homeDateText(now: Instant): String = homeFormat.format(now.atZone(HaweTime.SEOUL))
fun dotDate(date: LocalDate): String = dateFormat.format(date)
fun dotDate(instant: Instant): String = dotDate(instant.atZone(HaweTime.SEOUL).toLocalDate())
fun topicTimeText(time: LocalTime): String = "${if (time.hour < 12) "오전" else "오후"} ${((time.hour + 11) % 12) + 1}:${time.minute.toString().padStart(2, '0')}"
fun memberBadge(member: GroupMember): String? = listOfNotNull(
    "나".takeIf { member.isMe }, "방장".takeIf { member.isOwner }).takeIf { it.isNotEmpty() }?.joinToString(" · ")
fun leaveMessage(group: GroupDetail): String = buildString {
    if (group.amIOwner && group.members.size == 1)
        append("나가면 이 그룹이 삭제돼요. 초대 코드도 더는 쓸 수 없어요.")
    else append("'${group.name}'에서 나가도 내가 올린 작품과 댓글은 그룹에 남아요. 다시 들어오려면 초대 코드가 필요해요.")
}
fun groupCardSubtitle(summary: GroupSummary, now: Instant = Instant.now()): String = buildString {
    append("${summary.relationship.displayName} · ${summary.members.size}명")
    summary.daysTogether(now)?.let { append(" · 함께한 지 ${it}일") }
}

fun datePickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
fun dateFromPickerMillis(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
fun isSelectableDate(millis: Long, todayInSeoul: LocalDate): Boolean = millis <= datePickerMillis(todayInSeoul)
