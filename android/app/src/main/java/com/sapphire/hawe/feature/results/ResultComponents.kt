package com.sapphire.hawe.feature.results

<<<<<<< HEAD
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
=======
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
>>>>>>> feat/ios-step1
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
<<<<<<< HEAD
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
=======
>>>>>>> feat/ios-step1
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
<<<<<<< HEAD
import com.sapphire.hawe.core.model.MissionSpec
import com.sapphire.hawe.core.model.MissionTemplates
import com.sapphire.hawe.core.model.Profile
import com.sapphire.hawe.core.model.Submission
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.components.HAvatar
import com.sapphire.hawe.designsystem.components.HSliderEndLabels
import com.sapphire.hawe.designsystem.components.HSliderMark
import com.sapphire.hawe.designsystem.components.HSliderTrack
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HRadius
import com.sapphire.hawe.designsystem.tokens.HType

internal fun imageModel(submission: Submission): Any? = localImageFile(submission) ?:
    submission.mediaURL?.takeIf { it.scheme != "file" }?.toString()

@Composable
fun PhotoArtwork(submission: Submission, height: Dp, modifier: Modifier = Modifier) {
    val model = imageModel(submission)
    var loaded by remember(model) { mutableStateOf(false) }
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(HRadius.card))
        .background(HColors.surfaceStrong), contentAlignment = Alignment.Center) {
        if (!loaded) Text("[${submission.author.nickname}의 사진]", style = HType.plex(14), color = HColors.textSecondary)
        if (model != null) AsyncImage(model = model, contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            onSuccess = { loaded = true }, onError = { loaded = false })
    }
}

@Composable
fun DrawingArtwork(submission: Submission, height: Dp, drawingSize: Dp,
    modifier: Modifier = Modifier, cornerRadius: Dp = HRadius.card) {
    val model = imageModel(submission)
    var loaded by remember(model) { mutableStateOf(false) }
    val shape = RoundedCornerShape(cornerRadius)
    Box(modifier.fillMaxWidth().height(height).clip(shape).background(HColors.bg)
        .border(1.dp, HColors.line, shape), contentAlignment = Alignment.Center) {
        if (!loaded) Text("[${submission.author.nickname}의 그림]", style = HType.plex(14), color = HColors.textSecondary)
        if (model != null) AsyncImage(model = model, contentDescription = null,
            modifier = Modifier.size(drawingSize), contentScale = ContentScale.Fit,
            onSuccess = { loaded = true }, onError = { loaded = false })
    }
}
@Composable
fun LikeButton(count: Int, isLiked: Boolean, onClick: () -> Unit) {
    Row(Modifier.height(40.dp).clickable(onClick = onClick)
        .semantics { contentDescription = "${if (isLiked) "좋아요 취소" else "좋아요"}, ${count}개" },
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        HIconView(if (isLiked) HIcon.HEART_FILL else HIcon.HEART, size = 20.dp)
        Text(count.toString(), style = HType.plex(14).copy(fontFeatureSettings = "tnum"), color = HColors.ink)
    }
}

@Composable
fun PendingMembers(members: List<Profile>) {
    Column {
        Text("아직 안 올렸어요", style = HType.plex(14, FontWeight.Bold), color = HColors.ink,
            modifier = Modifier.padding(bottom = 6.dp))
        members.forEach { member ->
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                HAvatar(member.avatar, size = 34.dp)
                Text(member.nickname, style = HType.plex(15, FontWeight.SemiBold), color = HColors.ink)
=======
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
>>>>>>> feat/ios-step1
                Spacer(Modifier.weight(1f))
                Text("아직", style = HType.caption13, color = HColors.textTertiary)
            }
        }
    }
}

<<<<<<< HEAD
private val templateAnswerTemplates = setOf(MissionTemplates.TEXT_ONE_WORD, MissionTemplates.TEXT_REVIEW,
    MissionTemplates.TEXT_BALANCE, MissionTemplates.SLIDER_POSITION, MissionTemplates.SLIDER_TASTE,
    MissionTemplates.SLIDER_CONSENSUS)

/** 새 틀(B5)이라 글 한 덩어리가 아닌 전용 모양([TemplateAnswer])으로 그리는 제출물인지. */
fun hasTemplateAnswer(template: String) = template in templateAnswerTemplates

/**
 * 제출물 본문. 새 틀은 틀별 모양(한 단어+이유, 별점+후기, 선택+이유, 슬라이더 위치)이고,
 * 그 밖의 글 틀은 지금까지처럼 글만 보여요. [large] 는 상세 화면용 큰 글씨.
 */
@Composable
fun TemplateAnswer(submission: Submission, spec: MissionSpec, large: Boolean = false) {
    val body = HType.plex(if (large) 17 else 15, lineHeightMultiple = 1.6f)
    when (submission.template) {
        MissionTemplates.TEXT_ONE_WORD -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(submission.text.orEmpty(), style = HType.plex(if (large) 30 else 22, FontWeight.Bold), color = HColors.ink)
            submission.caption?.let { Text(it, style = body, color = HColors.textBody) }
        }
        MissionTemplates.TEXT_REVIEW -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val stars = submission.number ?: 0
            Text(starsText(stars), Modifier.clearAndSetSemantics { contentDescription = "별점 ${stars}점" },
                style = HType.plex(if (large) 24 else 18), color = HColors.accent)
            Text(submission.text.orEmpty(), style = HType.plex(if (large) 19 else 16, lineHeightMultiple = 1.65f),
                color = HColors.ink)
        }
        MissionTemplates.TEXT_BALANCE -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BalancePick(submission.choice, spec, large)
            submission.caption?.let { Text(it, style = body, color = HColors.textBody) }
        }
        MissionTemplates.SLIDER_POSITION, MissionTemplates.SLIDER_TASTE ->
            SliderAnswer(null, submission.number, spec, large)
        MissionTemplates.SLIDER_CONSENSUS -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // 두 막대는 같은 눈금이라 양 끝 라벨은 아래에 한 번만
            SliderAnswer("내 위치", submission.number, spec, large, showEndLabels = false)
            SliderAnswer("평균 예측", submission.guess, spec, large)
        }
        else -> Text(submission.text ?: "", style = HType.plex(16, lineHeightMultiple = 1.65f), color = HColors.ink)
    }
}

@Composable
private fun BalancePick(choice: String?, spec: MissionSpec, large: Boolean) {
    val option = when (choice) { "A" -> spec.optionA; "B" -> spec.optionB; else -> null }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(if (large) 32.dp else 28.dp).background(HColors.accent, CircleShape),
            contentAlignment = Alignment.Center) {
            Text(choice.orEmpty(), style = HType.plex(14, FontWeight.Bold), color = HColors.onAccent)
        }
        Text(option?.takeIf { it.isNotBlank() } ?: "${choice.orEmpty()} 선택", Modifier.weight(1f),
            style = HType.plex(if (large) 20 else 17, FontWeight.SemiBold, 1.4f), color = HColors.ink)
    }
}

/** 읽기 전용 슬라이더 한 줄(임시 UI): 라벨·값, 막대 위 점, 양 끝 라벨. */
@Composable
private fun SliderAnswer(label: String?, value: Int?, spec: MissionSpec, large: Boolean, showEndLabels: Boolean = true) {
    value ?: return
    val description = listOfNotNull(label, "$value", spec.left?.let { "왼쪽 $it" }, spec.right?.let { "오른쪽 $it" })
        .joinToString(", ")
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.orEmpty(), Modifier.weight(1f), style = HType.caption13, color = HColors.textSecondary)
            Text(value.toString(), style = HType.plex(if (large) 24 else 18, FontWeight.Bold)
                .copy(fontFeatureSettings = "tnum"), color = HColors.ink)
        }
        HSliderTrack(listOf(HSliderMark(value, HColors.accent, if (large) 18.dp else 14.dp)))
        if (showEndLabels) HSliderEndLabels(spec.left, spec.right)
=======
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
>>>>>>> feat/ios-step1
    }
}
