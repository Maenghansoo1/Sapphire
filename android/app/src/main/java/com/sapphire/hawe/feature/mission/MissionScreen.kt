package com.sapphire.hawe.feature.mission

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.app.LocalStore
import com.sapphire.hawe.designsystem.HAvatar
import com.sapphire.hawe.designsystem.HButton
import com.sapphire.hawe.designsystem.HColors
import com.sapphire.hawe.designsystem.HDivider
import com.sapphire.hawe.designsystem.HIcon
import com.sapphire.hawe.designsystem.HIconView
import com.sapphire.hawe.designsystem.HLayout
import com.sapphire.hawe.designsystem.HNavBar
import com.sapphire.hawe.designsystem.HPillButton
import com.sapphire.hawe.designsystem.HRadius
import com.sapphire.hawe.designsystem.HScreen
import com.sapphire.hawe.designsystem.HToast
import com.sapphire.hawe.designsystem.HType
import com.sapphire.hawe.designsystem.NavLeading
import com.sapphire.hawe.designsystem.plex
import com.sapphire.hawe.model.MissionMember
import com.sapphire.hawe.model.SEOUL
import com.sapphire.hawe.model.TodayMission
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 05 그룹 · 오늘의 미션 (Figma 1:542)
 * 오늘 유형·주제, 다음 미션까지 카운트다운, 유형별 CTA, 멤버별 완료 여부, 안 한 멤버 콕 찌르기(하루 1회)
 * 미션 수행(06~09)은 이 화면 위에 전체 화면으로 띄워요 (iOS fullScreenCover 와 같은 방식).
 */
@Composable
fun MissionScreen(
    groupId: String,
    onBack: () -> Unit,
    onCalendar: () -> Unit,
    onGroupInfo: () -> Unit,
    onResults: (missionId: String) -> Unit,
) {
    val store = LocalStore.current
    var performing by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    val now by produceState(Instant.now()) {
        while (true) {
            delay(1000)
            value = Instant.now()
        }
    }
    LaunchedEffect(Unit) { store.refreshTodayMissions() }

    // 그룹을 나간 직후처럼 미션이 없으면 아무것도 그리지 않아요.
    val mission = store.todayMission(groupId) ?: return

    // 다음 미션 시간이 되면 새 미션으로 바꿔요.
    if (!now.isBefore(mission.closesAt)) {
        LaunchedEffect(mission.id) { store.refreshTodayMissions() }
    }

    Box(Modifier.fillMaxSize()) {
        HScreen {
            HNavBar(mission.group.name, modifier = Modifier.padding(horizontal = HLayout.screenPaddingCompact), leading = NavLeading.Back(onBack)) {
                HeaderIconRow {
                    HeaderIconButton(HIcon.Calendar, onClick = onCalendar)
                    HeaderIconButton(HIcon.More, onClick = onGroupInfo)
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = HLayout.screenPaddingCompact)
                    .padding(bottom = 32.dp),
            ) {
                MissionCard(mission, now, onPerform = { performing = true }, onResults = { onResults(mission.id) })
                StatusHeader(mission)
                mission.members.forEach { member ->
                    MemberStatusRow(member) {
                        store.poke(member.id, groupId)
                        toast = "${member.profile.nickname}님을 콕 찔렀어요"
                    }
                }
                if (!mission.didSubmit) LockNotice(mission, Modifier.padding(top = 20.dp))
            }
        }

        AnimatedVisibility(performing, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            MissionPerformScreen(
                type = mission.type,
                topic = mission.topic,
                onClose = { performing = false },
                onSubmit = { content ->
                    store.submit(content, groupId)
                    performing = false
                    toast = "오늘의 미션을 올렸어요"
                },
            )
        }

        HToast(toast, onDismiss = { toast = null })
    }
}

/** 테두리 line · 모서리 24 · 안쪽 22 · 간격 18 */
@Composable
private fun MissionCard(mission: TodayMission, now: Instant, onPerform: () -> Unit, onResults: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, HColors.line, RoundedCornerShape(HRadius.largeCard))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .height(32.dp)
                    .background(HColors.surface, CircleShape)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HIconView(mission.type.icon, 16.dp)
                Text("오늘은 ${mission.type.displayName}", style = plex(13, FontWeight.SemiBold), color = HColors.ink)
            }
            Spacer(Modifier.weight(1f))
            Text("${dateText(mission.opensAt)} · #${mission.number}", style = HType.caption13, color = HColors.textSecondary)
        }

        Text(mission.topic, style = plex(24, FontWeight.Bold, lineHeight = 1.35f), color = HColors.ink)

        Text(
            "다음 미션까지 ${countdown(mission.closesAt, now)}",
            style = HType.caption13.copy(fontFeatureSettings = "tnum"),
            color = HColors.textSecondary,
        )

        if (mission.didSubmit) HButton("친구들 작품 보기", onClick = onResults)
        else HButton(mission.type.actionTitle, onClick = onPerform)
    }
}

@Composable
private fun StatusHeader(mission: TodayMission) {
    Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("참여 현황", style = HType.sectionTitle, color = HColors.ink)
        Spacer(Modifier.weight(1f))
        Text("${mission.doneCount} / ${mission.members.size}명 완료", style = HType.caption13, color = HColors.textSecondary)
    }
}

/** 줄 높이 56 · 아바타 38 · 아래 구분선 */
@Composable
private fun MemberStatusRow(member: MissionMember, onPoke: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(56.dp)) {
        Row(Modifier.fillMaxWidth().align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            HAvatar(member.profile.avatar, 38.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                if (member.isMe) "${member.profile.nickname} (나)" else member.profile.nickname,
                style = plex(15, FontWeight.Medium),
                color = HColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            when {
                member.isDone -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    HIconView(HIcon.Check, 16.dp)
                    Text("완료", style = plex(13, FontWeight.SemiBold), color = HColors.ink)
                }
                member.isMe -> Text("아직 안 했어요", style = HType.caption13, color = HColors.textSecondary)
                else -> HPillButton(if (member.pokedToday) "찔렀어요" else "콕 찌르기", enabled = !member.pokedToday, onClick = onPoke)
            }
        }
        HDivider(Modifier.align(Alignment.BottomCenter))
    }
}

/** surface · 모서리 18 · 안쪽 18 · 자물쇠 22 + 14 */
@Composable
private fun LockNotice(mission: TodayMission, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(HColors.surface, RoundedCornerShape(18.dp))
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HIconView(HIcon.Lock, 22.dp, HColors.textSecondary)
        Text("내 미션을 올리면 친구들의 ${mission.type.subjectForm} 열려요.", style = plex(14), color = HColors.textBody)
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)

private fun dateText(instant: Instant): String = dateFormat.format(instant.atZone(SEOUL))

/** 남은 시간 HH:mm:ss */
private fun countdown(end: Instant, now: Instant): String {
    val seconds = Duration.between(now, end).seconds.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
}
