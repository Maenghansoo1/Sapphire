package com.sapphire.hawe.feature.mission

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.components.HAlert
import com.sapphire.hawe.designsystem.components.HButton
import com.sapphire.hawe.designsystem.components.HButtonKind
import com.sapphire.hawe.designsystem.layout.ctaBottomPadding
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HType
import java.io.File

@Composable
fun VoiceMissionScreen(topic: String, onClose: () -> Unit,
    onSubmit: (File, Double, List<Float>) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller = remember { VoiceRecorderController(context) }
    val ui = VoiceUiState(controller.isRecording, controller.hasRecording)
    var showsPermissionAlert by remember { mutableStateOf(false) }
    var requestingPermission by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        requestingPermission = false
        if (granted) controller.start() else showsPermissionAlert = true
    }
    DisposableEffect(owner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.onBackground()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); controller.release() }
    }
    val bottom = ctaBottomPadding(32.dp)
    Column(Modifier.fillMaxSize().background(HColors.bg).padding(horizontal = 20.dp)) {
        MissionHeader(ui.hasDraft, onClose)
        MissionTitle(MissionType.VOICE, topic)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val top = minOf(72.dp, maxOf(0.dp, maxHeight - 240.dp))
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(top))
                Text(voiceTimeText(controller.elapsed),
                    style = HType.plex(44, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"),
                    color = HColors.ink)
                Spacer(Modifier.height(28.dp))
                VoiceWaveform(controller.levels, if (controller.isPlaying) controller.playbackProgress else null)
                Spacer(Modifier.height(28.dp))
                Text("최대 30초", style = HType.caption13, color = HColors.textSecondary)
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).alpha(if (ui.controlsEnabled) 1f else 0.4f)
                .border(1.dp, HColors.border, CircleShape)
                .clickable(enabled = ui.controlsEnabled) { controller.reset() }
                .semantics { contentDescription = "다시 녹음" }, contentAlignment = Alignment.Center) {
                Text("다시", style = HType.plex(13, FontWeight.SemiBold), color = HColors.ink)
            }
            Box(Modifier.size(84.dp).background(HColors.accent, CircleShape)
                .clickable(enabled = !requestingPermission && !submitted) {
                    if (controller.isRecording) controller.stop()
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                        controller.start()
                    else { requestingPermission = true; permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                }.semantics { contentDescription = if (controller.isRecording) "녹음 멈추기" else "녹음 시작" },
                contentAlignment = Alignment.Center) {
                if (controller.isRecording) Box(Modifier.size(26.dp)
                    .background(HColors.onAccent, RoundedCornerShape(6.dp)))
                else Box(Modifier.size(28.dp).background(HColors.onAccent, CircleShape))
            }
            Box(Modifier.size(56.dp).alpha(if (ui.controlsEnabled) 1f else 0.4f)
                .border(1.dp, HColors.border, CircleShape)
                .clickable(enabled = ui.controlsEnabled) { controller.togglePlayback() }
                .semantics { contentDescription = if (controller.isPlaying) "일시정지" else "미리 듣기" },
                contentAlignment = Alignment.Center) {
                HIconView(if (controller.isPlaying) HIcon.PAUSE else HIcon.PLAY, size = 20.dp)
            }
        }
        HButton("올리기", enabled = ui.canSubmit && !submitted) {
            if (submitted) return@HButton
            submitted = true
            controller.stopPlayback()
            onSubmit(controller.file, controller.elapsed, controller.levels.filter { it >= 0f })
        }
        Spacer(Modifier.height(bottom))
    }
    if (showsPermissionAlert) HAlert("마이크 권한이 필요해요", "설정 > HA'WE 에서 마이크를 켜 주세요.",
        confirmTitle = "설정 열기", confirmKind = HButtonKind.PRIMARY,
        onCancel = { showsPermissionAlert = false }, onConfirm = {
            showsPermissionAlert = false
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)))
        })
}

@Composable
private fun VoiceWaveform(levels: List<Float>, progress: Float?) {
    Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        levels.forEachIndexed { index, level ->
            val bar = waveformBarState(index, levels.size, level, progress)
            Box(Modifier.width(4.dp).height(bar.heightDp.dp)
                .background(when {
                    !bar.recorded -> HColors.waveformIdle
                    bar.played -> HColors.ink
                    else -> HColors.textTertiary
                }, RoundedCornerShape(2.dp)))
        }
    }
}
