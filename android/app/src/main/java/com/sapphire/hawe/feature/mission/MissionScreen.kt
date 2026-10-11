package com.sapphire.hawe.feature.mission

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.model.MissionMember
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.TodayMission
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.layout.ctaBottomPadding
import com.sapphire.hawe.designsystem.tokens.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.delay

@Composable
fun MissionScreen(id: UUID, store: DataStore, onBack: () -> Unit, onGroupInfo: () -> Unit,
    submittedSignal: Boolean = false, onSubmittedHandled: () -> Unit = {},
    onPerform: (MissionType?) -> Unit = {}) {
    val mission = store.todayMission(id)
    if (mission == null) {
        Box(Modifier.fillMaxSize().background(HColors.bg))
        return
    }
    val toast = rememberHToastState()
    LaunchedEffect(submittedSignal) {
        if (submittedSignal) { toast.show("오늘의 미션을 올렸어요"); onSubmittedHandled() }
    }
    val debug = LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    // 개발용 유형 전환(디버그 전용). 서버 연결 단계에서 삭제해요.
    var debugType by remember(id) { mutableStateOf<MissionType?>(null) }
    val displayType = if (debug) debugType ?: mission.type else mission.type
    val bottom = ctaBottomPadding()
    val performAction = { onPerform(if (debug) debugType else null) }
    Box(Modifier.fillMaxSize().background(HColors.bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = HLayout.screenPaddingCompact)) {
            HNavBar(mission.group.name, leading = HNavLeading.BACK, onLeading = onBack, trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MissionHeaderIcon(HIcon.CALENDAR, "기록 캘린더") { toast.show("준비 중이에요") }
                    MissionHeaderIcon(HIcon.MORE, "그룹 정보", onGroupInfo)
                }
            })
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottom + 8.dp)) {
                MissionCard(mission, displayType, debug, onDebugType = {
                    val choices = MissionType.entries
                    debugType = if (debugType == null) choices.first() else
                        choices.getOrNull(choices.indexOf(debugType) + 1)
                }, onPerform = performAction,
                    onResults = { toast.show("준비 중이에요") })
                Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("참여 현황", style = HType.sectionTitle, color = HColors.ink)
                    Spacer(Modifier.weight(1f))
                    Text("${mission.doneCount} / ${mission.members.size}명 완료",
                        style = HType.caption13, color = HColors.textSecondary)
                }
                mission.members.forEach { member ->
                    MissionMemberRow(member, onPoke = {
                        if (!member.pokedToday) {
                            store.poke(member.id, id)
                            toast.show(pokeToastText(member.profile.nickname))
                        }
                    })
                }
                if (!mission.didSubmit) Row(Modifier.fillMaxWidth().padding(top = 20.dp)
                    .background(HColors.surface, RoundedCornerShape(18.dp)).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    HIconView(HIcon.LOCK, size = 22.dp, color = HColors.textSecondary)
                    Text(lockNoticeText(mission.type), style = HType.plex(14), color = HColors.textBody)
                }
            }
        }
        HToastHost(toast)
    }
}

@Composable
private fun MissionHeaderIcon(icon: HIcon, label: String, onClick: () -> Unit) {
    Box(Modifier.size(HLayout.minTouch).clickable(onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        HIconView(icon, size = 22.dp)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MissionCard(mission: TodayMission, displayType: MissionType, debug: Boolean,
    onDebugType: () -> Unit, onPerform: () -> Unit, onResults: () -> Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp, HColors.line, RoundedCornerShape(HRadius.largeCard))
        .padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.height(32.dp).background(HColors.surface, CircleShape)
                .then(if (debug) Modifier.combinedClickable(onClick = {}, onLongClick = onDebugType) else Modifier)
                .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HIconView(displayType.icon, size = 16.dp)
                Text("오늘은 ${displayType.displayName}", style = HType.plex(13, FontWeight.SemiBold), color = HColors.ink)
            }
            Spacer(Modifier.width(8.dp))
            Text("${missionDateText(mission.opensAt)} · #${mission.number}", Modifier.weight(1f),
                style = HType.caption13, color = HColors.textSecondary, textAlign = TextAlign.End,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(mission.topic, style = HType.plex(24, FontWeight.Bold, 1.35f), color = HColors.ink)
        MissionCountdown(mission.closesAt)
        HButton(if (mission.didSubmit) "친구들 작품 보기" else displayType.actionTitle,
            onClick = if (mission.didSubmit) onResults else onPerform)
    }
}

@Composable
private fun MissionCountdown(closesAt: Instant) {
    val owner = LocalLifecycleOwner.current
    var started by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            started = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var now by remember(closesAt) { mutableStateOf(Instant.now()) }
    LaunchedEffect(closesAt, started) {
        if (started) while (true) { now = Instant.now(); delay(1000) }
    }
    Text("다음 미션까지 ${countdownText(closesAt, now)}",
        style = HType.caption13.copy(fontFeatureSettings = "tnum"), color = HColors.textSecondary)
}

@Composable
private fun MissionMemberRow(member: MissionMember, onPoke: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().height(55.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HAvatar(member.profile.avatar, size = 38.dp)
            Text(memberDisplayName(member), Modifier.weight(1f),
                style = HType.plex(15, FontWeight.Medium), color = HColors.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            when {
                member.isDone -> Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    HIconView(HIcon.CHECK, size = 16.dp)
                    Text("완료", style = HType.plex(13, FontWeight.SemiBold), color = HColors.ink)
                }
                member.isMe -> Text("아직 안 했어요", style = HType.caption13, color = HColors.textSecondary)
                else -> Box(Modifier.height(HLayout.minTouch).clickable(enabled = !member.pokedToday, onClick = onPoke),
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.height(34.dp).border(1.dp,
                        if (member.pokedToday) HColors.line else HColors.border, CircleShape)
                        .padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text(if (member.pokedToday) "찔렀어요" else "콕 찌르기",
                            style = HType.plex(13, FontWeight.SemiBold),
                            color = if (member.pokedToday) HColors.textTertiary else HColors.ink)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(HColors.line))
    }
}
