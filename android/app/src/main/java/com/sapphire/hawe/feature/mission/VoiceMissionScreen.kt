package com.sapphire.hawe.feature.mission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sapphire.hawe.designsystem.HAlert
import com.sapphire.hawe.designsystem.HButton
import com.sapphire.hawe.designsystem.HButtonKind
import com.sapphire.hawe.designsystem.HColors
import com.sapphire.hawe.designsystem.HIcon
import com.sapphire.hawe.designsystem.HIconView
import com.sapphire.hawe.designsystem.HLayout
import com.sapphire.hawe.designsystem.HType
import com.sapphire.hawe.designsystem.bottomInsetPadding
import com.sapphire.hawe.designsystem.plainClickable
import com.sapphire.hawe.designsystem.plex
import com.sapphire.hawe.model.MissionType
import com.sapphire.hawe.model.SubmissionContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.log10

/**
 * 09 미션 수행 · 음성 (Figma 1:672)
 * 녹음(최대 30초)·정지·다시·미리 듣기, 파형 표시. m4a(AAC)
 */
@Composable
fun VoiceMissionScreen(topic: String, onClose: () -> Unit, onSubmit: (SubmissionContent) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(context, scope) }
    var showsPermissionAlert by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recorder.startRecording() else showsPermissionAlert = true
    }
    // 올리지 않고 닫으면 녹음 파일을 지워요.
    DisposableEffect(Unit) { onDispose { recorder.release(deleteFile = !submitted) } }

    fun toggleRecording() {
        when {
            recorder.isRecording -> recorder.stopRecording()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> recorder.startRecording()
            else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val canReview = recorder.hasRecording && !recorder.isRecording

    MissionScaffold(
        hasDraft = recorder.hasRecording || recorder.isRecording,
        onClose = onClose,
        overlay = {
            if (showsPermissionAlert) {
                HAlert(
                    "마이크 권한이 필요해요",
                    "설정 > HA'WE 에서 마이크를 켜 주세요.",
                    confirmTitle = "설정 열기",
                    confirmKind = HButtonKind.Primary,
                    onCancel = { showsPermissionAlert = false },
                    onConfirm = {
                        showsPermissionAlert = false
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                )
            }
        },
    ) {
        Column(
            Modifier
                .weight(1f)
                .navigationBarsPadding()
                .padding(horizontal = HLayout.screenPaddingCompact)
                .padding(bottom = bottomInsetPadding(32.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MissionTitle(MissionType.Voice, topic)

            // 위 72 · 간격 28
            Column(
                Modifier.padding(top = 72.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Text(timeText(recorder.elapsed), style = plex(44, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"), color = HColors.ink)
                Waveform(recorder.levels, recorder.playbackProgress.takeIf { recorder.isPlaying })
                Text("최대 ${VoiceRecorder.MAX_SECONDS}초", style = HType.caption13, color = HColors.textSecondary)
            }

            Spacer(Modifier.weight(1f).heightIn(min = 24.dp))

            // 다시 56 · 녹음 84 · 미리 듣기 56, 간격 36
            Row(Modifier.padding(bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .dimmed(canReview)
                        .border(1.dp, HColors.border, CircleShape)
                        .plainClickable(enabled = canReview) { recorder.reset() },
                    contentAlignment = Alignment.Center,
                ) { Text("다시", style = plex(13, FontWeight.SemiBold), color = HColors.ink) }

                Box(
                    Modifier
                        .size(84.dp)
                        .background(HColors.accent, CircleShape)
                        .plainClickable(onClick = ::toggleRecording),
                    contentAlignment = Alignment.Center,
                ) {
                    if (recorder.isRecording) {
                        // 녹음 중: 흰 네모(정지)
                        Box(Modifier.size(26.dp).background(HColors.onAccent, RoundedCornerShape(6.dp)))
                    } else {
                        // Figma 에는 녹음 중 모양만 있어서, 대기 모양은 흰 원으로 정했어요 (iOS 와 같음).
                        Box(Modifier.size(28.dp).background(HColors.onAccent, CircleShape))
                    }
                }

                Box(
                    Modifier
                        .size(56.dp)
                        .dimmed(canReview)
                        .border(1.dp, HColors.border, CircleShape)
                        .plainClickable(enabled = canReview) { recorder.togglePlayback() },
                    contentAlignment = Alignment.Center,
                ) { HIconView(if (recorder.isPlaying) HIcon.Pause else HIcon.Play, 20.dp) }
            }

            HButton("올리기", enabled = canReview) {
                recorder.stopPlayback()
                submitted = true
                onSubmit(
                    SubmissionContent.Voice(
                        file = Uri.fromFile(recorder.file),
                        durationSec = recorder.elapsed,
                        // 녹음 안 된 막대(-1)는 빼고, 결과 화면 파형으로 써요.
                        waveform = recorder.levels.filter { it >= 0f },
                    ),
                )
            }
        }
    }
}

/** 막대 4 · 간격 4 · 높이 80. 녹음된 구간은 ink, 남은 구간은 waveformIdle 8pt. 미리 듣기 중 안 들은 막대는 textTertiary. */
@Composable
fun Waveform(levels: List<Float>, progress: Float? = null, modifier: Modifier = Modifier) {
    Row(modifier.height(80.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        levels.forEachIndexed { index, level ->
            val recorded = level >= 0f
            val played = progress?.let { index.toFloat() / levels.size < it } ?: true
            val color = when {
                recorded && played -> HColors.ink
                recorded -> HColors.textTertiary
                else -> HColors.waveformIdle
            }
            Box(
                Modifier
                    .width(4.dp)
                    .height(if (recorded) (8 + 72 * level).dp else 8.dp)
                    .background(color, RoundedCornerShape(2.dp)),
            )
        }
    }
}

private fun timeText(seconds: Double): String {
    val total = seconds.toInt()
    return "%02d:%02d".format(total / 60, total % 60)
}

/** 녹음·미리 듣기 */
private class VoiceRecorder(private val context: Context, private val scope: CoroutineScope) {
    var isRecording by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var hasRecording by mutableStateOf(false)
        private set
    var elapsed by mutableStateOf(0.0)
        private set

    /** 0..1 소리 크기, 아직 녹음 안 된 막대는 -1 */
    var levels by mutableStateOf(List(BAR_COUNT) { -1f })
        private set
    var playbackProgress by mutableStateOf(0f)
        private set

    val file: File = newSubmissionFile(context, "m4a")

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private var startedAt = 0L

    fun startRecording() {
        reset()
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(128_000)
                setAudioChannels(1)
                setMaxDuration(MAX_SECONDS * 1000)
                setOutputFile(file.path)
                // 30초가 되면 저절로 멈춰요.
                setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stopRecording() }
                prepare()
                start()
            }
        } catch (e: Exception) {
            recorder.release()
            return
        }
        this.recorder = recorder
        isRecording = true
        startedAt = SystemClock.elapsedRealtime()
        ticker = scope.launch {
            while (isActive) {
                tickRecording()
                delay(100)
            }
        }
    }

    fun stopRecording() {
        val recorder = recorder ?: return
        ticker?.cancel()
        // 너무 짧게 녹음하면 stop() 이 실패해요 — 그때는 녹음이 없는 것으로 봐요.
        val ok = runCatching { recorder.stop() }.isSuccess
        recorder.release()
        this.recorder = null
        isRecording = false
        hasRecording = ok && elapsed > 0
        if (!ok) reset()
    }

    private fun tickRecording() {
        val recorder = recorder ?: return
        elapsed = minOf((SystemClock.elapsedRealtime() - startedAt) / 1000.0, MAX_SECONDS.toDouble())
        // -50dB ~ 0dB → 0 ~ 1
        val amplitude = recorder.maxAmplitude
        val level = if (amplitude <= 0) 0f else ((20 * log10(amplitude / 32767f) + 50) / 50).coerceIn(0f, 1f)
        val index = minOf(BAR_COUNT - 1, (elapsed / MAX_SECONDS * BAR_COUNT).toInt())
        levels = levels.toMutableList().also { it[index] = maxOf(it[index], level) }
    }

    fun togglePlayback() {
        if (isPlaying) {
            stopPlayback()
            return
        }
        if (!hasRecording) return
        val player = runCatching {
            MediaPlayer().apply {
                setDataSource(file.path)
                prepare()
                setOnCompletionListener { stopPlayback() }
                start()
            }
        }.getOrNull() ?: return
        this.player = player
        isPlaying = true
        ticker = scope.launch {
            while (isActive) {
                playbackProgress = player.currentPosition.toFloat() / player.duration.coerceAtLeast(100)
                delay(100)
            }
        }
    }

    fun stopPlayback() {
        ticker?.takeIf { !isRecording }?.cancel()
        player?.release()
        player = null
        isPlaying = false
        playbackProgress = 0f
    }

    fun reset() {
        ticker?.cancel()
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null
        stopPlayback()
        isRecording = false
        hasRecording = false
        elapsed = 0.0
        levels = List(BAR_COUNT) { -1f }
        file.delete()
    }

    fun release(deleteFile: Boolean) {
        ticker?.cancel()
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null
        player?.release()
        player = null
        if (deleteFile) file.delete()
    }

    companion object {
        const val MAX_SECONDS = 30

        /** Figma 파형 막대 수 */
        const val BAR_COUNT = 32
    }
}
