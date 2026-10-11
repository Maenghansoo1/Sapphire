package com.sapphire.hawe.feature.results

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.app.LocalStore
import com.sapphire.hawe.designsystem.HAlert
import com.sapphire.hawe.designsystem.HAvatar
import com.sapphire.hawe.designsystem.HColors
import com.sapphire.hawe.designsystem.HDivider
import com.sapphire.hawe.designsystem.HIcon
import com.sapphire.hawe.designsystem.HIconView
import com.sapphire.hawe.designsystem.HLayout
import com.sapphire.hawe.designsystem.HNavBar
import com.sapphire.hawe.designsystem.HRadius
import com.sapphire.hawe.designsystem.HScreen
import com.sapphire.hawe.designsystem.HType
import com.sapphire.hawe.designsystem.NavLeading
import com.sapphire.hawe.designsystem.plainClickable
import com.sapphire.hawe.designsystem.plex
import com.sapphire.hawe.model.MissionType
import com.sapphire.hawe.model.Submission
import com.sapphire.hawe.model.SubmissionComment

/**
 * 14 작품 상세 · 댓글 (사진 1:1082 / 그림 1:1130 / 글 1:1179 / 음성 1:1227)
 * 작품 + 좋아요(토글) + 댓글 목록 + 하단 댓글 입력. 내 댓글은 지울 수 있어요. (Figma 의 ⋯ 메뉴는 쓰지 않아요)
 */
@Composable
fun SubmissionDetailScreen(groupId: String, missionId: String, submissionId: String, onBack: () -> Unit) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val player = remember { VoicePlayer(scope) }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    var draft by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<SubmissionComment?>(null) }

    val result = store.result(groupId, missionId)
    val submission = result?.submissions?.firstOrNull { it.id == submissionId }

    fun send() {
        val body = draft.trim()
        if (body.isEmpty()) return
        store.addComment(body, submissionId, missionId, groupId)
        draft = ""
    }

    Box(Modifier.fillMaxSize()) {
        HScreen {
            Header(submission, result?.topic ?: "", onBack)
            if (submission == null) return@HScreen

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                Box(Modifier.padding(top = 12.dp).padding(horizontal = HLayout.screenPaddingCompact)) { Artwork(submission, player) }
                Row(
                    Modifier.padding(top = 6.dp).padding(horizontal = HLayout.screenPaddingCompact),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LikeButton(submission.likeCount, submission.likedByMe) { store.toggleLike(submissionId, missionId, groupId) }
                    likersText(submission)?.let {
                        Text(it, style = HType.caption13, color = HColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Comments(submission) { deleting = it }
            }

            CommentBar(submission, draft, onChange = { draft = it }, onSend = ::send)
        }

        deleting?.let { comment ->
            HAlert(
                "댓글을 지울까요?",
                message = null,
                confirmTitle = "지우기",
                onCancel = { deleting = null },
                onConfirm = {
                    store.deleteComment(comment.id, submissionId, missionId, groupId)
                    deleting = null
                },
            )
        }
    }
}

/** 뒤로 + 가운데(아바타 30 · 이름 15 · 날짜·유형 13), 아래 주제 13 */
@Composable
private fun Header(submission: Submission?, topic: String, onBack: () -> Unit) {
    Column(Modifier.padding(horizontal = HLayout.screenPaddingCompact)) {
        Box(contentAlignment = Alignment.Center) {
            HNavBar(leading = NavLeading.Back(onBack))
            if (submission != null) {
                Row(
                    Modifier.padding(horizontal = HLayout.minTouch + 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HAvatar(submission.author.avatar, 30.dp)
                    Text(submission.author.nickname, style = plex(15, FontWeight.SemiBold), color = HColors.ink, maxLines = 1)
                    Text(
                        "${submission.createdAt.koreanMonthDay()} · ${submission.type.displayName}",
                        style = HType.caption13,
                        color = HColors.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
        Text(topic, style = HType.caption13, color = HColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Artwork(submission: Submission, player: VoicePlayer) {
    when (submission.type) {
        MissionType.Photo -> PhotoArtwork(submission, 300.dp)
        MissionType.Draw -> DrawingArtwork(submission, 300.dp, drawingSize = 240.dp)
        // surface · 모서리 20 · 안쪽 24 · 19 Medium · 줄 높이 1.7
        MissionType.Write -> SelectionContainer {
            Text(
                submission.text ?: "",
                style = plex(19, FontWeight.Medium, lineHeight = 1.7f),
                color = HColors.ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HColors.surface, RoundedCornerShape(HRadius.card))
                    .padding(24.dp),
            )
        }
        MissionType.Voice -> VoiceArtwork(submission, player)
    }
}

/** 테두리 line · 모서리 20 · 안쪽 24 · 파형(4 · 간격 4 · 높이 60) + 재생 60 */
@Composable
private fun VoiceArtwork(submission: Submission, player: VoicePlayer) {
    val isPlaying = player.playingId == submission.id
    val digits = HType.caption13.copy(fontFeatureSettings = "tnum")
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, HColors.line, RoundedCornerShape(HRadius.card))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        VoiceBars(
            submission.waveform,
            progress = if (isPlaying) player.progress else 0f,
            barWidth = 4.dp,
            spacing = 4.dp,
            maxHeight = 60.dp,
            minHeight = 8.dp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(shortDuration(if (isPlaying) player.elapsed else 0.0), style = digits, color = HColors.textSecondary)
            Box(
                Modifier
                    .size(60.dp)
                    .background(HColors.accent, CircleShape)
                    .plainClickable { player.toggle(submission) },
                contentAlignment = Alignment.Center,
            ) { HIconView(if (isPlaying) HIcon.Pause else HIcon.Play, 22.dp, HColors.onAccent) }
            Text(shortDuration(submission.durationSec ?: 0.0), style = digits, color = HColors.textSecondary)
        }
    }
}

/** "지우, 서연님 외 2명이 좋아해요" */
private fun likersText(submission: Submission): String? {
    val names = submission.likerNames
    if (names.isEmpty()) return null
    val shown = names.take(2).joinToString(", ")
    val others = submission.likeCount - minOf(names.size, 2)
    return if (others > 0) "${shown}님 외 ${others}명이 좋아해요" else "${shown}님이 좋아해요"
}

/** 위 구분선 · 위 14 · 간격 16, 아바타 32 + 이름 13 SemiBold · 시간 12 + 본문 14 (줄 높이 1.55) */
@Composable
private fun Comments(submission: Submission, onDelete: (SubmissionComment) -> Unit) {
    Column {
        HDivider()
        Column(
            Modifier.fillMaxWidth().padding(top = 14.dp).padding(horizontal = HLayout.screenPaddingCompact),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("댓글 ${submission.comments.size}", style = plex(14, FontWeight.Bold), color = HColors.ink)
            submission.comments.forEach { comment ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HAvatar(comment.author.avatar, 32.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(comment.author.nickname, style = plex(13, FontWeight.SemiBold), color = HColors.ink)
                            Text(comment.createdAt.koreanRelative(), style = HType.caption12, color = HColors.textSecondary)
                            if (comment.isMine) {
                                // Figma 에 삭제 모양이 없어서 시간 옆 작은 글자로 뒀어요 (iOS 와 같음).
                                Text(
                                    "삭제",
                                    style = HType.caption12,
                                    color = HColors.textTertiary,
                                    modifier = Modifier.padding(start = 2.dp).plainClickable { onDelete(comment) },
                                )
                            }
                        }
                        Text(comment.body, style = plex(14, lineHeight = 1.55f), color = HColors.ink)
                    }
                }
            }
        }
    }
}

/** 위 테두리 border · 위 10 · 좌우 16 · 입력 알약 44 + 등록 44 */
@Composable
private fun CommentBar(submission: Submission, draft: String, onChange: (String) -> Unit, onSend: () -> Unit) {
    val canSend = draft.isNotBlank()
    Column(Modifier.fillMaxWidth().background(HColors.bg).navigationBarsPadding()) {
        HDivider(color = HColors.border)
        Row(
            Modifier.padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = onChange,
                singleLine = true,
                textStyle = plex(15).copy(color = HColors.ink),
                cursorBrush = SolidColor(HColors.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .border(1.dp, HColors.border, CircleShape)
                    .padding(horizontal = 16.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (draft.isEmpty()) {
                            Text(
                                "${submission.author.nickname}님에게 댓글 남기기",
                                style = plex(15),
                                color = HColors.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
            Box(
                Modifier
                    .height(44.dp)
                    .background(if (canSend) HColors.accent else HColors.disabled, CircleShape)
                    .plainClickable(enabled = canSend, onClick = onSend)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("등록", style = plex(15, FontWeight.SemiBold), color = if (canSend) HColors.onAccent else HColors.textSecondary)
            }
        }
    }
}
