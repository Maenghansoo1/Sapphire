package com.sapphire.hawe.feature.mission

<<<<<<< HEAD
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
=======
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
>>>>>>> feat/ios-step1
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
<<<<<<< HEAD
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.sapphire.hawe.core.dev.AppClock
import com.sapphire.hawe.core.net.DevRepository
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
    submittedLate: Boolean = false, submittedMissionId: Long? = null,
    refreshSignal: Boolean = false, onRefreshHandled: () -> Unit = {},
    notice: String? = null, onNoticeHandled: () -> Unit = {},
    onPerform: (Long, MissionType?) -> Unit = { _, _ -> }, onResults: (Long) -> Unit = {},
    onCalendar: () -> Unit = {}, dev: DevRepository? = null) {
    val group = store.groups.firstOrNull { it.id == id } ?: return
    val currentMissions = store.todayMissions(id)
    var selectedId by rememberSaveable(id) { mutableStateOf<Long?>(null) }
    val mission = selectedMission(currentMissions, selectedId)
    LaunchedEffect(currentMissions.map { it.id }, selectedId) {
        if (selectedId != null && currentMissions.none { it.id == selectedId })
            selectedId = currentMissions.firstOrNull()?.id
    }
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
            delay(Duration.between(AppClock.now(), nextPublishAt).toMillis().coerceAtLeast(0))
            repeat(3) { attempt ->
                refresh++
                if (attempt < 2) delay(30_000)
            }
        }
    }
    LaunchedEffect(submittedSignal, submittedMissionId) {
        if (submittedSignal) {
            val index = currentMissions.indexOfFirst { it.id == submittedMissionId }.coerceAtLeast(0)
            if (submittedMissionId != null) selectedId = submittedMissionId
            toast.show(submittedToastText(index, currentMissions.size, submittedLate))
            onSubmittedHandled()
        }
    }
    LaunchedEffect(refreshSignal) { if (refreshSignal) { refresh++; onRefreshHandled() } }
    LaunchedEffect(notice) { if (notice != null) { toast.show(notice); onNoticeHandled() } }
    val scope = rememberCoroutineScope()
    var creating by remember(id) { mutableStateOf(false) }
    var rerollConfirmation by remember(id) { mutableStateOf<Long?>(null) }
    fun createMission() {
        if (dev == null || creating) return
        creating = true
        scope.launch {
            try {
                val result = dev.publish(id)
                refresh++
                toast.show(result.message ?: "미션 ${result.created.size}개를 생성했어요")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                toast.show("미션 생성 실패: ${error.message}")
            } finally { creating = false }
        }
    }
    fun rerollMission(missionId: Long) {
        if (dev == null || creating) return
        creating = true
        scope.launch {
            try {
                val result = dev.reroll(missionId)
                store.clearMissionForReroll(id, missionId)
                refresh++
                toast.show("미션을 새로 뽑았어요 (${result.template})")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                toast.show("미션 리롤 실패: ${error.message}")
            } finally { creating = false }
        }
    }
    var sending by remember(id) { mutableStateOf(emptySet<Triple<Long, Long, PokeKind>>()) }
    fun sendPoke(missionID: Long, member: MissionMember, kind: PokeKind) {
        val key = Triple(missionID, member.id, kind)
        if (key in sending) return
        sending = sending + key
        scope.launch {
            try {
                val sent = pokes.send(id, member.id, kind, missionID)
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
            if (currentMissions.size >= 2) Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 임시 UI: 슬롯별 선택 칩
                currentMissions.forEachIndexed { index, item ->
                    val selected = item.id == mission?.id
                    Row(Modifier.background(if (selected) HColors.ink else HColors.surface, CircleShape)
                        .clickable { selectedId = item.id }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${index + 1}", style = HType.plex(14, FontWeight.SemiBold),
                            color = if (selected) HColors.bg else HColors.ink)
                        if (item.didSubmit) HIconView(HIcon.CHECK_SMALL, size = 14.dp,
                            color = if (selected) HColors.bg else HColors.ink)
                    }
                }
            }
            when {
                group.missionState == MissionState.NOT_LOADED -> Unit
                group.missionState == MissionState.FAILED && mission == null -> MissionLoadFailed { refresh++ }
                mission == null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("아직 오늘의 미션이 열리지 않았어요", style = HType.sectionTitle, color = HColors.ink)
                    nextPublishAt?.let { MissionCountdown(it) }
                    if (dev != null) HButton("[개발자] 미션 생성", Modifier.padding(top = 20.dp),
                        enabled = !creating, onClick = ::createMission)
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottom + 8.dp)) {
                MissionCard(mission, currentMissions.indexOfFirst { it.id == mission.id }, currentMissions.size,
                    onPerform = { onPerform(mission.id, null) }, onResults = { onResults(mission.id) })
                if (dev != null) HButton("[개발자] 미션 생성", Modifier.padding(top = 12.dp),
                    enabled = !creating, onClick = ::createMission)
                if (dev != null) HButton("[개발자] 미션 리롤", Modifier.padding(top = 8.dp),
                    enabled = !creating, onClick = { rerollConfirmation = mission.id })
                Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("참여 현황", style = HType.sectionTitle, color = HColors.ink)
                    Spacer(Modifier.weight(1f))
                    Text("${mission.doneCount} / ${mission.members.size}명 완료",
                        style = HType.caption13, color = HColors.textSecondary)
                }
                mission.members.forEach { member ->
                    MissionMemberRow(member, sending.filter { it.first == mission.id && it.second == member.id }
                        .map { it.third }.toSet()) { kind ->
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
    rerollConfirmation?.let { missionId ->
        HAlert("이 미션을 새로 뽑을까요?", "이미 낸 제출·반응·댓글·콕이 모두 삭제돼요.", "리롤",
            onCancel = { rerollConfirmation = null }, onConfirm = {
                rerollConfirmation = null
                rerollMission(missionId)
            })
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
private fun MissionCard(mission: TodayMission, index: Int, total: Int,
    onPerform: () -> Unit, onResults: () -> Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp, HColors.line, RoundedCornerShape(HRadius.largeCard))
        .padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.height(32.dp).background(HColors.surface, CircleShape).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                mission.type?.let { HIconView(it.icon, size = 16.dp) }
                Text("오늘은 ${mission.type?.displayName ?: "미션"}", style = HType.plex(13, FontWeight.SemiBold), color = HColors.ink)
            }
            Spacer(Modifier.width(8.dp))
            Text((if (total >= 2) "${index + 1}번째 · " else "") +
                "${missionDateText(mission.opensAt)} · #${mission.number}", Modifier.weight(1f),
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
=======
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
>>>>>>> feat/ios-step1
    }
}

@Composable
<<<<<<< HEAD
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
    var now by remember(closesAt) { mutableStateOf(AppClock.now()) }
    LaunchedEffect(closesAt, started) {
        if (started) while (true) { now = AppClock.now(); delay(1000) }
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
=======
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
>>>>>>> feat/ios-step1
}
