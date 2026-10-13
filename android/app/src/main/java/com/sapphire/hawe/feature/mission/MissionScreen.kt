package com.sapphire.hawe.feature.mission

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.sapphire.hawe.core.data.MissionState
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.net.MissionRepository
import com.sapphire.hawe.core.net.PokeFollowUp
import com.sapphire.hawe.core.net.PokeRepository
import com.sapphire.hawe.core.net.classifyPokeFailure
import com.sapphire.hawe.core.model.MissionMember
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.PokeKind
import com.sapphire.hawe.core.model.TodayMission
import com.sapphire.hawe.core.model.supportsTemplate
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.layout.ctaBottomPadding
import com.sapphire.hawe.designsystem.tokens.*
import java.time.Instant
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun MissionScreen(id: Long, store: DataStore, missions: MissionRepository, pokes: PokeRepository, session: AppSession,
    onBack: () -> Unit, onGroupInfo: () -> Unit,
    submittedSignal: Boolean = false, onSubmittedHandled: () -> Unit = {},
    submittedLate: Boolean = false, refreshSignal: Boolean = false, onRefreshHandled: () -> Unit = {},
    notice: String? = null, onNoticeHandled: () -> Unit = {},
    onPerform: (MissionType?) -> Unit = {}, onResults: () -> Unit = {}, onCalendar: () -> Unit = {}) {
    val group = store.groups.firstOrNull { it.id == id } ?: return
    val mission = store.todayMission(id)
    val toast = rememberHToastState()
    var refresh by remember(id) { mutableIntStateOf(0) }
    LaunchedEffect(id, refresh) {
        try {
            val state = missions.current(id, session.account.userId)
            if (session.state == AppSession.State.SIGNED_IN) store.applyCurrentMission(id, state)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (session.state == AppSession.State.SIGNED_IN) {
                if (store.todayMission(id) == null) store.markMissionFailed(id)
                else toast.show("네트워크 연결을 확인해 주세요")
            }
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, id) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val nextPublishAt = group.nextPublishAt
    LaunchedEffect(id, nextPublishAt) {
        if (nextPublishAt != null) {
            delay(Duration.between(Instant.now(), nextPublishAt).toMillis().coerceAtLeast(0))
            repeat(3) { attempt ->
                refresh++
                if (attempt < 2) delay(30_000)
            }
        }
    }
    LaunchedEffect(submittedSignal) {
        if (submittedSignal) { toast.show(if (submittedLate) "지각으로 올렸어요" else "오늘의 미션을 올렸어요"); onSubmittedHandled() }
    }
    LaunchedEffect(refreshSignal) { if (refreshSignal) { refresh++; onRefreshHandled() } }
    LaunchedEffect(notice) { if (notice != null) { toast.show(notice); onNoticeHandled() } }
    val scope = rememberCoroutineScope()
    var sending by remember(id) { mutableStateOf(emptySet<Pair<Long, PokeKind>>()) }
    fun sendPoke(missionID: Long, member: MissionMember, kind: PokeKind) {
        val key = member.id to kind
        if (key in sending) return
        sending = sending + key
        scope.launch {
            try {
                val sent = pokes.send(id, member.id, kind)
                sending = sending - key
                if (session.state != AppSession.State.SIGNED_IN) return@launch
                store.markPoked(id, sent.missionId, member.id, sent.kind)
                toast.show(pokeSentText(kind, member.profile.nickname))
            } catch (error: Exception) {
                sending = sending - key
                if (error is CancellationException) throw error
                if (session.state != AppSession.State.SIGNED_IN) return@launch
                val failure = classifyPokeFailure(error)
                when (failure.followUp) {
                    PokeFollowUp.MARK_SENT -> { store.markPoked(id, missionID, member.id, kind); refresh++ }
                    PokeFollowUp.REFRESH -> refresh++
                    PokeFollowUp.LEAVE -> onBack()
                    PokeFollowUp.NONE -> Unit
                }
                failure.message?.let { toast.show(it) }
            }
        }
    }
    val bottom = ctaBottomPadding()
    Box(Modifier.fillMaxSize().background(HColors.bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = HLayout.screenPaddingCompact)) {
            HNavBar(group.detail.name, leading = HNavLeading.BACK, onLeading = onBack, trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MissionHeaderIcon(HIcon.CALENDAR, "기록 캘린더", onCalendar)
                    MissionHeaderIcon(HIcon.MORE, "그룹 정보", onGroupInfo)
                }
            })
            when {
                group.missionState == MissionState.NOT_LOADED -> Unit
                group.missionState == MissionState.FAILED && mission == null -> MissionLoadFailed { refresh++ }
                mission == null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("아직 오늘의 미션이 열리지 않았어요", style = HType.sectionTitle, color = HColors.ink)
                    nextPublishAt?.let { MissionCountdown(it) }
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottom + 8.dp)) {
                MissionCard(mission, onPerform = { onPerform(null) }, onResults = onResults)
                Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("참여 현황", style = HType.sectionTitle, color = HColors.ink)
                    Spacer(Modifier.weight(1f))
                    Text("${mission.doneCount} / ${mission.members.size}명 완료",
                        style = HType.caption13, color = HColors.textSecondary)
                }
                mission.members.forEach { member ->
                    MissionMemberRow(member, sending.filter { it.first == member.id }.map { it.second }.toSet()) { kind ->
                        sendPoke(mission.id, member, kind)
                    }
                }
                if (!mission.didSubmit && mission.type != null && supportsTemplate(mission.template)) Row(Modifier.fillMaxWidth().padding(top = 20.dp)
                    .background(HColors.surface, RoundedCornerShape(18.dp)).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    HIconView(HIcon.LOCK, size = 22.dp, color = HColors.textSecondary)
                    Text(lockNoticeText(mission.type, mission.template), style = HType.plex(14), color = HColors.textBody)
                }
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
private fun MissionCard(mission: TodayMission, onPerform: () -> Unit, onResults: () -> Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp, HColors.line, RoundedCornerShape(HRadius.largeCard))
        .padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.height(32.dp).background(HColors.surface, CircleShape).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                mission.type?.let { HIconView(it.icon, size = 16.dp) }
                Text("오늘은 ${mission.type?.displayName ?: "미션"}", style = HType.plex(13, FontWeight.SemiBold), color = HColors.ink)
            }
            Spacer(Modifier.width(8.dp))
            Text("${missionDateText(mission.opensAt)} · #${mission.number}", Modifier.weight(1f),
                style = HType.caption13, color = HColors.textSecondary, textAlign = TextAlign.End,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(mission.topic, style = HType.plex(24, FontWeight.Bold, 1.35f), color = HColors.ink)
            templateGuideText(mission.template, mission.spec)?.let {
                Text(it, style = HType.plex(14), color = HColors.textSecondary)
            }
        }
        MissionCountdown(mission.closesAt, mission.deadlineAt)
        if (mission.type == null || !supportsTemplate(mission.template)) Text(UNSUPPORTED_MISSION_TEXT,
            style = HType.caption13, color = HColors.textSecondary)
        else HButton(if (mission.didSubmit) resultsActionTitle(mission.template)
            else missionActionTitle(mission.type, mission.template),
            onClick = if (mission.didSubmit) onResults else onPerform)
    }
}

@Composable
private fun MissionCountdown(closesAt: Instant, deadlineAt: Instant? = null) {
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
    Text(if (now.isBefore(closesAt)) "다음 미션까지 ${countdownText(closesAt, now)}" else "곧 열려요",
        style = HType.caption13.copy(fontFeatureSettings = "tnum"), color = HColors.textSecondary)
    deadlineAt?.let { deadlineText(it, closesAt, now)?.let { label ->
        Text(label, style = HType.caption13, color = HColors.textSecondary)
    } }
}

@Composable
private fun MissionMemberRow(member: MissionMember, sending: Set<PokeKind>, onPoke: (PokeKind) -> Unit) {
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
                else -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PokeKind.entries.forEach { kind ->
                        PokeButton(kind, member.profile.nickname, sent = kind in member.poked,
                            busy = kind in sending) { onPoke(kind) }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(HColors.line))
    }
}

/** 콕·똥 버튼. 보낸 종류는 체크와 강조색으로 바뀌고 다시 누를 수 없다(미션마다 종류별 1번). */
@Composable
private fun PokeButton(kind: PokeKind, nickname: String, sent: Boolean, busy: Boolean, onClick: () -> Unit) {
    val description = pokeButtonDescription(kind, nickname, sent)
    val color = if (sent) HColors.accentPressed else HColors.ink
    Row(Modifier.minimumInteractiveComponentSize().height(34.dp).clip(CircleShape)
        .background(if (sent) HColors.accentSubtle else HColors.bg)
        .border(1.dp, if (sent) HColors.accentSubtle else HColors.border, CircleShape)
        .clickable(enabled = !sent && !busy, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description }
        .alpha(if (busy) 0.5f else 1f).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        HIconView(if (sent) HIcon.CHECK_SMALL else kind.icon, size = 16.dp, color = color)
        Text(pokeButtonLabel(kind), Modifier.clearAndSetSemantics { },
            style = HType.plex(13, FontWeight.SemiBold), color = color)
    }
}

private val PokeKind.icon: HIcon get() = when (this) {
    PokeKind.POKE -> HIcon.POKE_TEMP
    PokeKind.FLING -> HIcon.FLING_TEMP
}
