package com.sapphire.hawe.feature.mission

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.components.HButton
import com.sapphire.hawe.designsystem.components.HButtonKind
import com.sapphire.hawe.designsystem.layout.ctaBottomPadding
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HRadius
import com.sapphire.hawe.designsystem.tokens.HType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PhotoMissionScreen(topic: String, onClose: () -> Unit, onSubmit: (ByteArray) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    LaunchedEffect(Unit) { if (!permission) permissionLauncher.launch(Manifest.permission.CAMERA) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var submitted by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val controller = rememberPhotoCameraController(permission && photo == null)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && !processing) {
            processing = true
            scope.launch {
                try { withContext(Dispatchers.Default) { loadCroppedPhoto(context, uri) }?.let { photo = it } }
                finally { processing = false }
            }
        }
    }
    val bottom = ctaBottomPadding(40.dp)
    Column(Modifier.fillMaxSize().background(HColors.bg).padding(horizontal = 20.dp)) {
        MissionHeader(photo != null, onClose, trailing = {
            if (photo == null && controller.ready) Box(Modifier.size(44.dp)
                .clickable { controller.toggleFlash() }
                .semantics { contentDescription = if (controller.flash) "플래시 끄기" else "플래시 켜기" },
                contentAlignment = Alignment.Center) {
                HIconView(HIcon.FLASH, size = 22.dp,
                    color = if (controller.flash) HColors.ink else HColors.textTertiary)
            }
        })
        Spacer(Modifier.height(16.dp))
        MissionTitle(MissionType.PHOTO, topic)
        Spacer(Modifier.height(16.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val width = minOf(maxWidth, maxHeight * 0.75f)
            Box(Modifier.width(width).height(width * 4f / 3f)
                .clip(RoundedCornerShape(HRadius.largeCard)).background(HColors.surfaceStrong),
                contentAlignment = Alignment.Center) {
                when {
                    photo != null -> Image(photo!!.asImageBitmap(), null, Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    controller.ready -> CameraPreviewView(controller)
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HIconView(HIcon.CAMERA, size = 34.dp, color = HColors.textTertiary)
                        Text(if (!permission) "설정에서 카메라 권한을 켜 주세요" else "앨범에서 사진을 골라 주세요",
                            style = HType.plex(14), color = HColors.textSecondary)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (photo == null) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            PhotoIconButton(HIcon.IMAGE, "앨범에서 고르기", false, !processing) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            Box(Modifier.size(84.dp).alpha(if (controller.ready && !processing) 1f else 0.4f)
                .border(4.dp, HColors.accent, CircleShape)
                .clickable(enabled = controller.ready && !controller.capturing && !processing) {
                    controller.takePicture(context) { file ->
                        if (!scope.coroutineContext.isActive) {
                            file.delete()
                            return@takePicture
                        }
                        processing = true
                        scope.launch {
                            try { withContext(Dispatchers.Default) { loadCroppedPhoto(context, Uri.fromFile(file)) }?.let { photo = it } }
                            finally { file.delete(); processing = false }
                        }
                    }
                }.semantics { contentDescription = "사진 찍기" }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(64.dp).background(HColors.accent, CircleShape))
            }
            PhotoIconButton(HIcon.FLIP, "카메라 전환", true, controller.ready && controller.canFlip && !processing) { controller.flip() }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HButton("다시 찍기", Modifier.weight(1f), HButtonKind.SECONDARY) {
                photo = null
            }
            HButton("올리기", Modifier.weight(1f), enabled = !submitted) {
                val current = photo ?: return@HButton
                if (!submitted) {
                    submitted = true
                    scope.launch {
                        try { onSubmit(withContext(Dispatchers.Default) { photoJpegBytes(current) }) }
                        catch (error: Exception) { submitted = false }
                    }
                }
            }
        }
        Spacer(Modifier.height(bottom))
    }
}

@Composable
private fun PhotoIconButton(icon: HIcon, label: String, round: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = if (round) CircleShape else RoundedCornerShape(14.dp)
    Box(Modifier.size(52.dp).alpha(if (enabled) 1f else 0.4f)
        .border(1.dp, HColors.border, shape).clickable(enabled = enabled, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        HIconView(icon, size = 22.dp)
    }
}
