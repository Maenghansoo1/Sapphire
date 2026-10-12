package com.sapphire.hawe.feature.results

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.net.ApiException
import com.sapphire.hawe.core.net.SubmissionRepository
import com.sapphire.hawe.core.model.MissionResult
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.Submission
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.components.HAvatar
import com.sapphire.hawe.designsystem.components.HButton
import com.sapphire.hawe.designsystem.components.HNavBar
import com.sapphire.hawe.designsystem.components.HNavLeading
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HType
import kotlinx.coroutines.CancellationException

const val LATE_LABEL = "지각"

@Composable
fun ResultsScreen(groupID: Long, missionID: Long, store: DataStore, repository: SubmissionRepository,
    session: AppSession, onBack: () -> Unit, onDetail: (Long) -> Unit,
    onLocked: () -> Unit, onHome: () -> Unit) {
    var refresh by remember(missionID) { mutableIntStateOf(0) }
    var failed by remember(missionID) { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(missionID, refresh) {
        failed = false
        try {
            val feed = repository.feed(missionID, session.account.userId)
            if (session.state == AppSession.State.SIGNED_IN) store.applyFeed(groupID, missionID, feed)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is ApiException && error.code == "FEED_LOCKED") onLocked()
            else if (error is ApiException && error.code in setOf("NOT_GROUP_MEMBER", "MISSION_NOT_FOUND")) onHome()
            else failed = true
        }
    }
    DisposableEffect(owner, missionID) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val result = store.result(groupID, missionID)
    if (failed) {
        Column(Modifier.fillMaxSize().background(HColors.bg), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("결과를 불러오지 못했어요", style = HType.sectionTitle, color = HColors.ink)
            Spacer(Modifier.height(16.dp))
            HButton("다시 시도") { refresh++ }
        }
        return
    }
    if (result == null) { Box(Modifier.fillMaxSize().background(HColors.bg)); return }
    val player = remember { VoicePlayerController() }
    DisposableEffect(player, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.stop() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); player.release() }
    }
    Column(Modifier.fillMaxSize().background(HColors.bg).padding(horizontal = 20.dp)) {
        HNavBar("${koreanMonthDayText(result.date)} · ${result.type.displayName}",
            leading = HNavLeading.BACK, onLeading = onBack,
            titleStyle = HType.plex(15, FontWeight.SemiBold))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Column(Modifier.padding(top = 8.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(result.topic, style = HType.plex(22, FontWeight.Bold), color = HColors.ink)
                Text("${progressText(result.submissions.size, result.memberCount)} · ${hintText(result.type)}",
                    style = HType.caption13, color = HColors.textSecondary)
            }
            when (result.type) {
                MissionType.PHOTO -> PhotoFeed(result, onDetail)
                MissionType.DRAW -> DrawingGrid(result, onDetail)
                MissionType.WRITE -> TextCards(result, onDetail)
                MissionType.VOICE -> VoiceList(result, player, onDetail)
            }
            if (result.pending.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                PendingMembers(result.pending)
            }
        }
    }
}

@Composable
private fun PhotoFeed(result: MissionResult, onDetail: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        result.submissions.forEach { submission ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HAvatar(submission.author.avatar, size = 34.dp)
                    Text(submission.author.nickname, Modifier.weight(1f), style = HType.plex(15, FontWeight.SemiBold),
                        color = HColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(koreanTimeText(submission.createdAt), style = HType.caption13, color = HColors.textSecondary)
                    if (submission.isLate) Text(LATE_LABEL, style = HType.caption12, color = HColors.textSecondary)
                }
                PhotoArtwork(submission, 260.dp, Modifier.clickable { onDetail(submission.id) })
            }
        }
    }
}

@Composable
private fun DrawingGrid(result: MissionResult, onDetail: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        result.submissions.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { submission ->
                    Column(Modifier.weight(1f).clickable { onDetail(submission.id) },
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DrawingArtwork(submission, 165.dp, 130.dp, cornerRadius = 18.dp)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            HAvatar(submission.author.avatar, size = 26.dp)
                            Text(submission.author.nickname, Modifier.weight(1f),
                                style = HType.plex(14, FontWeight.SemiBold), color = HColors.ink,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (submission.isLate) Text(LATE_LABEL, style = HType.caption12, color = HColors.textSecondary)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TextCards(result: MissionResult, onDetail: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        result.submissions.forEach { submission ->
            Column(Modifier.fillMaxWidth().background(HColors.surface, RoundedCornerShape(20.dp))
                .clickable { onDetail(submission.id) }.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    HAvatar(submission.author.avatar, Modifier.background(HColors.bg, CircleShape), size = 30.dp)
                    Text(submission.author.nickname, Modifier.weight(1f), style = HType.plex(14, FontWeight.SemiBold),
                        color = HColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(koreanTimeText(submission.createdAt), style = HType.caption12, color = HColors.textSecondary)
                    if (submission.isLate) Text(LATE_LABEL, style = HType.caption12, color = HColors.textSecondary)
                }
                Text(submission.text ?: "", style = HType.plex(16, lineHeightMultiple = 1.65f), color = HColors.ink)
            }
        }
    }
}

@Composable
private fun VoiceList(result: MissionResult, player: VoicePlayerController, onDetail: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp).border(1.dp, HColors.border, RoundedCornerShape(14.dp))
            .clickable { player.playAll(result.submissions) }, horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(16.dp))
            HIconView(HIcon.PLAY, size = 16.dp)
            Text("전체 이어 듣기", style = HType.plex(15, FontWeight.SemiBold), color = HColors.ink)
        }
        result.submissions.forEach { submission ->
            Row(Modifier.fillMaxWidth().border(1.dp, HColors.line, RoundedCornerShape(18.dp)).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val playing = player.playingID == submission.id
                Box(Modifier.size(44.dp).background(if (playing) HColors.accent else HColors.ink, CircleShape)
                    .clickable { player.toggle(submission) }
                    .semantics { contentDescription = if (playing) "일시정지" else "${submission.author.nickname} 음성 재생" },
                    contentAlignment = Alignment.Center) {
                    HIconView(if (playing) HIcon.PAUSE else HIcon.PLAY, size = 16.dp, color = HColors.onAccent)
                }
                Column(Modifier.weight(1f).clickable { onDetail(submission.id) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HAvatar(submission.author.avatar, size = 24.dp)
                        Text(submission.author.nickname, Modifier.weight(1f), style = HType.plex(14, FontWeight.SemiBold),
                            color = HColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${shortDurationText(submission.duration ?: 0.0)}${if (submission.isLate) " · $LATE_LABEL" else ""}",
                            style = HType.caption12, color = HColors.textSecondary)
                    }
                    VoiceBars(submission.waveform, if (playing) player.progress else 0f)
                }
            }
        }
    }
}
