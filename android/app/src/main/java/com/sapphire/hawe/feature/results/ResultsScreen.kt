package com.sapphire.hawe.feature.results

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.app.LocalStore
import com.sapphire.hawe.designsystem.HAvatar
import com.sapphire.hawe.designsystem.HColors
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
import com.sapphire.hawe.model.MissionResult
import com.sapphire.hawe.model.MissionType
import com.sapphire.hawe.model.Submission

/**
 * 결과 공개 — 10 사진(세로 피드) · 11 그림(2열 격자) · 12 글(카드) · 13 음성(목록 + 전체 이어 듣기)
 * 작품을 누르면 14 작품 상세 · 댓글로 가요. 이 화면에는 댓글 입력이 없어요.
 * 내가 제출해야 볼 수 있어요 — 서버(RLS)에서도 막아야 해요 (CLAUDE.md §5-2).
 */
@Composable
fun ResultsScreen(groupId: String, missionId: String, onBack: () -> Unit, onOpen: (submissionId: String) -> Unit) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val player = remember { VoicePlayer(scope) }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    val result = store.result(groupId, missionId)

    HScreen {
        HNavBar(
            result?.let { "${it.date.koreanMonthDay()} · ${it.type.displayName}" },
            modifier = Modifier.padding(horizontal = HLayout.screenPaddingCompact),
            titleStyle = plex(15, FontWeight.SemiBold),
            leading = NavLeading.Back(onBack),
        )
        if (result == null) return@HScreen

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = HLayout.screenPaddingCompact)
                .padding(bottom = 32.dp),
        ) {
            Title(result)
            when (result.type) {
                MissionType.Photo -> PhotoFeed(result.submissions, onOpen) { store.toggleLike(it, missionId, groupId) }
                MissionType.Draw -> DrawingGrid(result.submissions, onOpen)
                MissionType.Write -> TextCards(result.submissions, onOpen)
                MissionType.Voice -> VoiceList(result.submissions, player, onOpen)
            }
            if (result.pending.isNotEmpty()) PendingMembers(result.pending, Modifier.padding(top = 28.dp))
        }
    }
}

/** 주제 22 Bold + 완료 현황 13 (위 8 · 아래 18) */
@Composable
private fun Title(result: MissionResult) {
    val done = result.submissions.size
    val progress = if (done == result.memberCount) "${done}명 모두 완료" else "$done / ${result.memberCount}명 완료"
    val hint = if (result.type == MissionType.Voice) "눌러서 듣고 댓글을 남겨보세요" else "${result.type.displayName}을 눌러 댓글을 남겨보세요"
    Column(Modifier.padding(top = 8.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(result.topic, style = plex(22, FontWeight.Bold), color = HColors.ink)
        Text("$progress · $hint", style = HType.caption13, color = HColors.textSecondary)
    }
}

// MARK: - 10 사진

@Composable
private fun PhotoFeed(submissions: List<Submission>, onOpen: (String) -> Unit, onLike: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        submissions.forEach { submission ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    HAvatar(submission.author.avatar, 34.dp)
                    Text(submission.author.nickname, style = plex(15, FontWeight.SemiBold), color = HColors.ink)
                    Text(submission.createdAt.koreanTime(), style = HType.caption13, color = HColors.textSecondary)
                }
                Box(Modifier.plainClickable { onOpen(submission.id) }) { PhotoArtwork(submission, 260.dp) }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    LikeButton(submission.likeCount, submission.likedByMe) { onLike(submission.id) }
                    CommentCount(submission.comments.size) { onOpen(submission.id) }
                }
            }
        }
    }
}

// MARK: - 11 그림

/** 2열 · 가로 12 · 세로 14 · 칸 165 높이 · 모서리 18 */
@Composable
private fun DrawingGrid(submissions: List<Submission>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        submissions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { submission ->
                    Column(
                        Modifier.weight(1f).plainClickable { onOpen(submission.id) },
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DrawingArtwork(submission, 165.dp, drawingSize = 130.dp, cornerRadius = 18.dp)
                        Row(Modifier.padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            HAvatar(submission.author.avatar, 26.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                submission.author.nickname,
                                style = plex(14, FontWeight.SemiBold),
                                color = HColors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("댓글 ${submission.comments.size}", style = HType.caption12, color = HColors.textSecondary)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// MARK: - 12 글

/** surface · 모서리 20 · 안쪽 18 · 간격 12, 본문 16 · 줄 높이 1.65 */
@Composable
private fun TextCards(submissions: List<Submission>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        submissions.forEach { submission ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(HColors.surface, RoundedCornerShape(HRadius.card))
                    .plainClickable { onOpen(submission.id) }
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    HAvatar(submission.author.avatar, 30.dp, Modifier.background(HColors.bg, CircleShape))
                    Text(submission.author.nickname, style = plex(14, FontWeight.SemiBold), color = HColors.ink)
                    Text(submission.createdAt.koreanTime(), style = HType.caption12, color = HColors.textSecondary)
                }
                Text(submission.text ?: "", style = plex(16, lineHeight = 1.65f), color = HColors.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("좋아요 ${submission.likeCount}", style = HType.caption12, color = HColors.textSecondary)
                    Text("댓글 ${submission.comments.size}", style = HType.caption12, color = HColors.textSecondary)
                }
            }
        }
    }
}

// MARK: - 13 음성

@Composable
private fun VoiceList(submissions: List<Submission>, player: VoicePlayer, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .border(1.dp, HColors.border, RoundedCornerShape(HRadius.input))
                .plainClickable { player.playAll(submissions) },
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HIconView(HIcon.Play, 16.dp)
            Text("전체 이어 듣기", style = plex(15, FontWeight.SemiBold), color = HColors.ink)
        }
        submissions.forEach { VoiceRow(it, player, onOpen) }
    }
}

/** 테두리 line · 모서리 18 · 안쪽 14 · 재생 44 (멈춤 ink / 재생 중 accent) */
@Composable
private fun VoiceRow(submission: Submission, player: VoicePlayer, onOpen: (String) -> Unit) {
    val isPlaying = player.playingId == submission.id
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, HColors.line, RoundedCornerShape(18.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(if (isPlaying) HColors.accent else HColors.ink, CircleShape)
                .plainClickable { player.toggle(submission) },
            contentAlignment = Alignment.Center,
        ) { HIconView(if (isPlaying) HIcon.Pause else HIcon.Play, 16.dp, HColors.onAccent) }

        Column(Modifier.weight(1f).plainClickable { onOpen(submission.id) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HAvatar(submission.author.avatar, 24.dp)
                Spacer(Modifier.width(6.dp))
                Text(submission.author.nickname, style = plex(14, FontWeight.SemiBold), color = HColors.ink)
                Spacer(Modifier.weight(1f).width(4.dp))
                Text(
                    "${shortDuration(submission.durationSec ?: 0.0)} · 댓글 ${submission.comments.size}",
                    style = HType.caption12,
                    color = HColors.textSecondary,
                )
            }
            VoiceBars(submission.waveform, progress = if (isPlaying) player.progress else 0f)
        }
    }
}
