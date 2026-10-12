package com.sapphire.hawe.feature.results

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.Submission
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.components.HAvatar
import com.sapphire.hawe.designsystem.components.HNavBar
import com.sapphire.hawe.designsystem.components.HNavLeading
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HType

@Composable
fun SubmissionDetailScreen(groupID: Long, missionID: Long, submissionID: Long, store: DataStore,
    onBack: () -> Unit) {
    val result = store.result(groupID, missionID)
    val submission = result?.submissions?.find { it.id == submissionID }
    if (result == null || submission == null) {
        Box(Modifier.fillMaxSize().background(HColors.bg))
        return
    }
    val player = remember { VoicePlayerController() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(player, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.stop() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); player.release() }
    }
    Column(Modifier.fillMaxSize().background(HColors.bg)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Box {
                HNavBar(leading = HNavLeading.BACK, onLeading = onBack)
                Row(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 52.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    HAvatar(submission.author.avatar, size = 30.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(submission.author.nickname, style = HType.plex(15, FontWeight.SemiBold),
                            color = HColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${koreanMonthDayText(submission.createdAt)} · ${result.type.displayName}${if (submission.isLate) " · $LATE_LABEL" else ""}",
                            style = HType.caption13, color = HColors.textSecondary, maxLines = 1)
                    }
                }
            }
            Text(result.topic, Modifier.fillMaxWidth(), style = HType.caption13,
                color = HColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            DetailArtwork(submission, result.type, player)
        }
    }
}

@Composable
private fun DetailArtwork(submission: Submission, type: MissionType, player: VoicePlayerController) {
    when (type) {
        MissionType.PHOTO -> PhotoArtwork(submission, 300.dp)
        MissionType.DRAW -> DrawingArtwork(submission, 300.dp, 240.dp)
        MissionType.WRITE -> SelectionContainer {
            Text(submission.text ?: "", Modifier.fillMaxWidth()
                .background(HColors.surface, RoundedCornerShape(20.dp)).padding(24.dp),
                style = HType.plex(19, FontWeight.Medium, 1.7f), color = HColors.ink)
        }
        MissionType.VOICE -> Column(Modifier.fillMaxWidth().border(1.dp, HColors.line, RoundedCornerShape(20.dp))
            .padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            VoiceBars(submission.waveform, if (player.playingID == submission.id) player.progress else 0f,
                barWidth = 4.dp, spacing = 4.dp, maxHeight = 60.dp, minHeight = 8.dp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(shortDurationText(if (player.playingID == submission.id) player.elapsed else 0.0),
                    style = HType.caption13.copy(fontFeatureSettings = "tnum"), color = HColors.textSecondary)
                val playing = player.playingID == submission.id
                Box(Modifier.size(60.dp).clip(CircleShape).background(HColors.accent)
                    .clickable { player.toggle(submission) }
                    .semantics { contentDescription = if (playing) "일시정지" else "재생" },
                    contentAlignment = Alignment.Center) {
                    HIconView(if (playing) HIcon.PAUSE else HIcon.PLAY, size = 22.dp, color = HColors.onAccent)
                }
                Text(shortDurationText(submission.duration ?: 0.0),
                    style = HType.caption13.copy(fontFeatureSettings = "tnum"), color = HColors.textSecondary)
            }
        }
    }
}
