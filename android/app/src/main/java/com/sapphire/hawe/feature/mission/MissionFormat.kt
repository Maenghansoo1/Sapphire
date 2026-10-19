package com.sapphire.hawe.feature.mission

import com.sapphire.hawe.core.HaweTime
import com.sapphire.hawe.core.josa
import com.sapphire.hawe.core.model.MissionMember
import com.sapphire.hawe.core.model.MissionSpec
import com.sapphire.hawe.core.model.MissionTemplates
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.TodayMission
import com.sapphire.hawe.core.model.PokeKind
import com.sapphire.hawe.core.model.isAggregateTemplate
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

fun selectedMission(missions: List<TodayMission>, selectedId: Long?): TodayMission? =
    missions.firstOrNull { it.id == selectedId } ?: missions.firstOrNull()

fun submittedToastText(index: Int, total: Int, late: Boolean): String = when {
    total <= 1 -> if (late) "지각으로 올렸어요" else "오늘의 미션을 올렸어요"
    late -> "${index + 1}번째 미션을 지각으로 올렸어요"
    else -> "${index + 1}번째 미션을 올렸어요"
}

fun lockNoticeText(type: MissionType, template: String = ""): String =
    if (isAggregateTemplate(template)) "내 답을 올리면 친구들의 답과 그룹 결과가 열려요."
    else "내 미션을 올리면 친구들의 ${type.subjectForm} 열려요."

/** 서버가 새 틀을 켰는데 이 앱에 그 화면이 없을 때(구형 앱) 05 카드에 보여요. */
const val UNSUPPORTED_MISSION_TEXT = "앱을 업데이트하면 이 미션을 할 수 있어요"

/** 05 카드의 수행 버튼. 밸런스 게임은 고르는 거라, 갤러리 발굴은 찍지 않고 갤러리에서 찾는 거라 따로 불러요. */
fun missionActionTitle(type: MissionType, template: String): String = when (template) {
    MissionTemplates.TEXT_BALANCE -> "하나 고르기"
    MissionTemplates.PHOTO_GALLERY -> "갤러리 둘러보기"
    else -> type.actionTitle
}

/** 제출한 뒤 05 카드 버튼. 집계 틀은 결과 카드가 먼저 보여서 "결과 보기"예요. */
fun resultsActionTitle(template: String): String =
    if (isAggregateTemplate(template)) "결과 보기" else "작품 보기"

/** 새 틀의 방법 안내(05 카드·수행 화면 제목 아래). 기존 틀은 주제 문장만으로 충분해서 null — "A를 B로"는 뜻이 잘 안 전해져서 안내를 둬요. */
fun templateGuideText(template: String, spec: MissionSpec): String? = when (template) {
    MissionTemplates.TEXT_ONE_WORD -> "띄어쓰기 없이 한 단어로 남겨요"
    MissionTemplates.TEXT_REVIEW -> "별점 1~5개와 한 줄 후기를 남겨요"
    MissionTemplates.TEXT_BALANCE -> {
        val a = spec.optionA?.takeIf { it.isNotBlank() }
        val b = spec.optionB?.takeIf { it.isNotBlank() }
        if (a != null && b != null) "‘$a’ vs ‘$b’ 둘 중 하나를 골라요" else "둘 중 하나를 골라요"
    }
    MissionTemplates.SLIDER_POSITION, MissionTemplates.SLIDER_TASTE -> sliderEnds(spec)
        ?.let { "$it 사이에서 내 위치를 골라요" } ?: "내 위치를 골라요"
    MissionTemplates.SLIDER_CONSENSUS -> "내 위치와 우리 그룹 평균을 함께 예측해요"
    MissionTemplates.DRAW_A_TO_B -> drawToGuide(spec)
    else -> null
}

private fun sliderEnds(spec: MissionSpec): String? {
    val left = spec.left?.takeIf { it.isNotBlank() } ?: return null
    val right = spec.right?.takeIf { it.isNotBlank() } ?: return null
    return "‘$left’ ↔ ‘$right’"
}

/** A를 B로: A 를 B 처럼 보이게 바꿔 그리기. A 가 사람([대상])이면 이름을 빼고 B 만 말해요. */
private fun drawToGuide(spec: MissionSpec): String {
    val to = spec.drawTo?.takeIf { it.isNotBlank() } ?: return "주제를 다른 모습으로 바꿔서 그려요"
    val from = spec.drawFrom?.takeIf { it.isNotBlank() && !spec.hasTarget && "[대상]" !in it }
    return if (from != null) "‘$from’${josa(from, "을", "를")} ‘$to’처럼 보이게 바꿔서 그려요"
    else "‘$to’처럼 보이게 바꿔서 그려요"
}
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
