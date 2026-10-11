package com.sapphire.hawe.feature.results

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.sapphire.hawe.designsystem.components.HAlert
import com.sapphire.hawe.designsystem.components.HAvatar
import com.sapphire.hawe.designsystem.components.HNavBar
import com.sapphire.hawe.designsystem.components.HNavLeading
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HType
import java.time.Instant
import java.util.UUID

@Composable
fun SubmissionDetailScreen(groupID: UUID, missionID: UUID, submissionID: UUID, store: DataStore,
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
    var draft by rememberSaveable(submissionID) { mutableStateOf("") }
    var deleting by remember { mutableStateOf<UUID?>(null) }
    val send = {
        val body = draft.trim()
        if (body.isNotEmpty()) {
            store.addComment(body, submissionID, missionID, groupID)
            draft = ""
        }
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
                        Text("${koreanMonthDayText(submission.createdAt)} · ${result.type.displayName}",
                            style = HType.caption13, color = HColors.textSecondary, maxLines = 1)
                    }
                }
            }
            Text(result.topic, Modifier.fillMaxWidth(), style = HType.caption13,
                color = HColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                DetailArtwork(submission, result.type, player)
            }
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                LikeButton(submission.likeCount, submission.likedByMe) {
                    store.toggleLike(submissionID, missionID, groupID)
                }
                val likers = likersText(submission.likerNames, submission.likeCount)
                if (likers != null) Text(likers, style = HType.caption13,
                    color = HColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.fillMaxWidth().height(1.dp).background(HColors.line))
                Text("댓글 ${submission.comments.size}", Modifier.padding(top = 14.dp, bottom = 16.dp),
                    style = HType.plex(14, FontWeight.Bold), color = HColors.ink)
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    submission.comments.forEach { comment ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HAvatar(comment.author.avatar, size = 32.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(comment.author.nickname, style = HType.plex(13, FontWeight.SemiBold),
                                        color = HColors.ink)
                                    Text(koreanRelativeText(comment.createdAt, Instant.now()),
                                        style = HType.caption12, color = HColors.textSecondary)
                                    if (comment.isMine) Text("삭제", Modifier.clickable { deleting = comment.id },
                                        style = HType.caption12, color = HColors.textTertiary)
                                }
                                Text(comment.body, style = HType.plex(14, lineHeightMultiple = 1.55f),
                                    color = HColors.ink)
                            }
                        }
                    }
                }
            }
        }
        CommentInput(draft, onChange = { draft = it }, onSend = send,
            nickname = submission.author.nickname)
    }
    deleting?.let { commentID ->
        HAlert(title = "댓글을 지울까요?", message = "", confirmTitle = "지우기",
            onCancel = { deleting = null }, onConfirm = {
                store.deleteComment(commentID, submissionID, missionID, groupID)
                deleting = null
            })
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

@Composable
private fun CommentInput(draft: String, onChange: (String) -> Unit, onSend: () -> Unit, nickname: String) {
    Column(Modifier.fillMaxWidth().background(HColors.bg)) {
    Spacer(Modifier.fillMaxWidth().height(1.dp).background(HColors.border))
    Row(Modifier.fillMaxWidth()
        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(44.dp).border(1.dp, HColors.border, CircleShape)
            .padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            BasicTextField(value = draft, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
                textStyle = HType.plex(15).copy(color = HColors.ink), cursorBrush = SolidColor(HColors.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }), singleLine = true,
                decorationBox = { inner ->
                    Box {
                        if (draft.isEmpty()) Text(commentPlaceholder(nickname), style = HType.plex(15),
                            color = HColors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        inner()
                    }
                })
        }
        val enabled = commentSendEnabled(draft)
        Box(Modifier.height(44.dp).clip(CircleShape)
            .background(if (enabled) HColors.accent else HColors.disabled)
            .clickable(enabled = enabled, onClick = onSend).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center) {
            Text("등록", style = HType.plex(15, FontWeight.SemiBold),
                color = if (enabled) HColors.onAccent else HColors.textSecondary)
        }
    }
    }
}
