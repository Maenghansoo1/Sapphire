package com.sapphire.hawe.feature.results

import android.media.MediaPlayer
import android.os.SystemClock
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sapphire.hawe.designsystem.HAvatar
import com.sapphire.hawe.designsystem.HColors
import com.sapphire.hawe.designsystem.HIcon
import com.sapphire.hawe.designsystem.HIconView
import com.sapphire.hawe.designsystem.HRadius
import com.sapphire.hawe.designsystem.HType
import com.sapphire.hawe.designsystem.plainClickable
import com.sapphire.hawe.designsystem.plex
import com.sapphire.hawe.model.MissionType
import com.sapphire.hawe.model.Profile
import com.sapphire.hawe.model.SEOUL
import com.sapphire.hawe.model.Submission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 사진 작품 — surfaceStrong · 모서리 20. 파일이 없으면(체험용 그룹) "[민준의 사진]" 자리 표시. */
@Composable
fun PhotoArtwork(submission: Submission, height: Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(HRadius.card))
            .background(HColors.surfaceStrong),
        contentAlignment = Alignment.Center,
    ) {
        val media = submission.media
        if (media != null) {
            AsyncImage(media.toString(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text("[${submission.author.nickname}의 사진]", style = plex(14), color = HColors.textSecondary)
        }
    }
}

/** 그림 작품 — 흰 바탕 + 테두리 line. 그림은 칸 안 [drawingSize] 에 맞춰요 (Figma: 165 칸에 130, 300 칸에 240). */
@Composable
fun DrawingArtwork(submission: Submission, height: Dp, drawingSize: Dp, cornerRadius: Dp = HRadius.card) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(HColors.bg)
            .border(1.dp, HColors.line, shape),
        contentAlignment = Alignment.Center,
    ) {
        val media = submission.media
        if (media != null) {
            AsyncImage(media.toString(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(drawingSize))
        } else {
            Text("[${submission.author.nickname}의 그림]", style = plex(14), color = HColors.textSecondary)
        }
    }
}

/** 음성 파형 — 재생한 만큼 accent, 나머지는 controlBorder */
@Composable
fun VoiceBars(
    levels: List<Float>,
    progress: Float = 0f,
    barWidth: Dp = 3.dp,
    spacing: Dp = 3.dp,
    maxHeight: Dp = 24.dp,
    minHeight: Dp = 6.dp,
) {
    Row(Modifier.height(maxHeight), horizontalArrangement = Arrangement.spacedBy(spacing), verticalAlignment = Alignment.CenterVertically) {
        levels.forEachIndexed { index, level ->
            val played = index.toFloat() / levels.size.coerceAtLeast(1) < progress
            Box(
                Modifier
                    .width(barWidth)
                    .height(maxOf(minHeight, maxHeight * level))
                    .background(if (played) HColors.accent else HColors.controlBorder, RoundedCornerShape(barWidth / 2)),
            )
        }
    }
}

/** 좋아요 (하트 20 + 수 14) — 누른 상태는 채운 하트(ink) */
@Composable
fun LikeButton(count: Int, isLiked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.height(40.dp).plainClickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HIconView(if (isLiked) HIcon.HeartFill else HIcon.Heart, 20.dp)
        Text("$count", style = plex(14).copy(fontFeatureSettings = "tnum"), color = HColors.ink)
    }
}

/** 댓글 아이콘 20 + 수 14 */
@Composable
fun CommentCount(count: Int, onClick: () -> Unit) {
    Row(
        Modifier.height(40.dp).plainClickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HIconView(HIcon.Comment, 20.dp)
        Text("$count", style = plex(14), color = HColors.ink)
    }
}

/** 아직 올리지 않은 멤버 — Figma 에 없어서 목록 끝에 '아직' 으로 보여 줘요 (CLAUDE.md §5-2, iOS 와 같음) */
@Composable
fun PendingMembers(members: List<Profile>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text("아직 안 올렸어요", style = plex(14, FontWeight.Bold), color = HColors.ink, modifier = Modifier.padding(bottom = 6.dp))
        members.forEach { member ->
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                HAvatar(member.avatar, 34.dp)
                Spacer(Modifier.width(10.dp))
                Text(member.nickname, style = plex(15, FontWeight.SemiBold), color = HColors.ink)
                Spacer(Modifier.weight(1f))
                Text("아직", style = HType.caption13, color = HColors.textTertiary)
            }
        }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)
private val monthDayFormat = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)

/** "오전 9:12" */
fun Instant.koreanTime(): String = timeFormat.format(atZone(SEOUL))

/** "10월 11일" */
fun Instant.koreanMonthDay(): String = monthDayFormat.format(atZone(SEOUL))

/** "2시간 전" — 1분 안이면 "방금" */
fun Instant.koreanRelative(now: Instant = Instant.now()): String {
    val seconds = Duration.between(this, now).seconds
    return when {
        seconds < 60 -> "방금"
        seconds < 3600 -> "${seconds / 60}분 전"
        seconds < 86_400 -> "${seconds / 3600}시간 전"
        else -> "${seconds / 86_400}일 전"
    }
}

/** "0:09" */
fun shortDuration(seconds: Double): String {
    val total = seconds.toInt()
    return "${total / 60}:" + "%02d".format(total % 60)
}

/**
 * 음성 작품 재생 — 한 번에 하나씩, '전체 이어 듣기'면 끝나면 다음 것으로 넘어가요.
 * 파일이 없는 체험용 작품은 길이만큼 재생하는 척해요 (iOS 와 같음).
 */
class VoicePlayer(private val scope: CoroutineScope) {
    var playingId by mutableStateOf<String?>(null)
        private set

    /** 0..1 */
    var progress by mutableStateOf(0f)
        private set
    var elapsed by mutableStateOf(0.0)
        private set

    private var queue: List<Submission> = emptyList()
    private var player: MediaPlayer? = null
    private var job: Job? = null

    fun toggle(submission: Submission) {
        if (playingId == submission.id) {
            stop()
        } else {
            queue = emptyList()
            play(submission)
        }
    }

    fun playAll(submissions: List<Submission>) {
        val voices = submissions.filter { it.type == MissionType.Voice }
        val first = voices.firstOrNull() ?: return
        queue = voices.drop(1)
        play(first)
    }

    fun stop() {
        job?.cancel()
        job = null
        player?.release()
        player = null
        playingId = null
        progress = 0f
        elapsed = 0.0
    }

    private fun play(submission: Submission) {
        stop()
        playingId = submission.id
        val path = submission.media?.path
        val player = path?.let {
            runCatching {
                MediaPlayer().apply {
                    setDataSource(it)
                    prepare()
                    start()
                }
            }.getOrNull()
        }
        this.player = player
        val duration = player?.let { it.duration / 1000.0 } ?: submission.durationSec ?: 1.0
        val started = SystemClock.elapsedRealtime()
        job = scope.launch {
            while (isActive) {
                delay(100)
                elapsed = player?.let { it.currentPosition / 1000.0 } ?: ((SystemClock.elapsedRealtime() - started) / 1000.0)
                progress = (elapsed / duration.coerceAtLeast(0.1)).toFloat().coerceAtMost(1f)
                val finished = player?.let { !it.isPlaying } ?: (elapsed >= duration)
                if (finished || progress >= 1f) {
                    val next = queue.firstOrNull()
                    queue = queue.drop(1)
                    if (next != null) play(next) else stop()
                    break
                }
            }
        }
    }
}
