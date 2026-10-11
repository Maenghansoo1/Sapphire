package com.sapphire.hawe.feature.mission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sapphire.hawe.designsystem.HButton
import com.sapphire.hawe.designsystem.HButtonKind
import com.sapphire.hawe.designsystem.HColors
import com.sapphire.hawe.designsystem.HIcon
import com.sapphire.hawe.designsystem.HIconView
import com.sapphire.hawe.designsystem.HLayout
import com.sapphire.hawe.designsystem.HRadius
import com.sapphire.hawe.designsystem.bottomInsetPadding
import com.sapphire.hawe.designsystem.plainClickable
import com.sapphire.hawe.designsystem.plex
import com.sapphire.hawe.model.MissionType
import com.sapphire.hawe.model.SubmissionContent
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 07 미션 수행 · 사진 (Figma 1:638)
 * 카메라(플래시, 전·후면 전환) + 앨범 선택. 3:4 로 잘라서 올려요.
 */
@Composable
fun PhotoMissionScreen(topic: String, onClose: () -> Unit, onSubmit: (SubmissionContent) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var isDenied by remember { mutableStateOf(false) }
    var isAvailable by remember { mutableStateOf(false) }
    var isFlashOn by remember { mutableStateOf(false) }
    var useBack by remember { mutableStateOf(true) }
    var isCapturing by remember { mutableStateOf(false) }

    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val previewView = remember {
        PreviewView(context).apply {
            // 둥근 모서리로 잘리도록 TextureView 방식
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        isDenied = !granted
    }
    val albumLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) loadPhoto(context, uri)?.let { photo = it.croppedToThreeByFour() }
    }

    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    // 찍은 사진을 보는 동안은 카메라를 꺼 두고, 다시 찍을 때 켜요.
    LaunchedEffect(hasPermission, useBack, photo == null) {
        if (!hasPermission) return@LaunchedEffect
        val cameraProvider = provider ?: cameraProvider(context).also { provider = it }
        cameraProvider.unbindAll()
        if (photo != null) return@LaunchedEffect
        val selector = if (useBack) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        // 카메라가 없는 기기는 앨범만 써요.
        isAvailable = runCatching {
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
        }.isSuccess
    }
    DisposableEffect(Unit) { onDispose { provider?.unbindAll() } }

    fun capture() {
        if (!isAvailable || isCapturing) return
        isCapturing = true
        imageCapture.flashMode = if (isFlashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    photo = image.uprightBitmap().croppedToThreeByFour()
                    image.close()
                    isCapturing = false
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                }
            },
        )
    }

    MissionScaffold(
        hasDraft = photo != null,
        onClose = onClose,
        trailing = {
            if (photo == null && isAvailable) {
                HeaderIconRow {
                    HeaderIconButton(HIcon.Flash, tint = if (isFlashOn) HColors.ink else HColors.textTertiary) { isFlashOn = !isFlashOn }
                }
            }
        },
    ) {
        Column(
            Modifier
                .weight(1f)
                .navigationBarsPadding()
                .padding(horizontal = HLayout.screenPaddingCompact)
                .padding(bottom = bottomInsetPadding(40.dp)),
        ) {
            Column(
                Modifier.weight(1f).padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MissionTitle(MissionType.Photo, topic)
                // surfaceStrong · 모서리 24 · 3:4
                Box(
                    Modifier
                        .weight(1f, fill = false)
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(HRadius.largeCard))
                        .background(HColors.surfaceStrong),
                    contentAlignment = Alignment.Center,
                ) {
                    val current = photo
                    when {
                        current != null -> Image(current.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        isAvailable -> AndroidView({ previewView }, Modifier.fillMaxSize())
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            HIconView(HIcon.Camera, 34.dp, HColors.textTertiary)
                            Text(
                                if (isDenied) "설정에서 카메라 권한을 켜 주세요" else "앨범에서 사진을 골라 주세요",
                                style = plex(14),
                                color = HColors.textSecondary,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            if (photo == null) {
                CaptureControls(
                    cameraEnabled = isAvailable,
                    onAlbum = { albumLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onCapture = ::capture,
                    onFlip = { useBack = !useBack },
                )
            } else {
                // Figma 에 찍은 뒤 화면이 없어서 '다시 찍기 / 올리기' 두 버튼으로 정했어요 (iOS 와 같음).
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HButton("다시 찍기", Modifier.weight(1f), kind = HButtonKind.Secondary) { photo = null }
                    HButton("올리기", Modifier.weight(1f)) {
                        val bitmap = photo ?: return@HButton
                        val uri = writeSubmissionFile(context, "jpg") { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                        onSubmit(SubmissionContent.Image(uri))
                    }
                }
            }
        }
    }
}

/** 좌우 20 · 앨범 52(모서리 14) · 셔터 84 · 전환 52(원) */
@Composable
private fun CaptureControls(cameraEnabled: Boolean, onAlbum: () -> Unit, onCapture: () -> Unit, onFlip: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(52.dp)
                .border(1.dp, HColors.border, RoundedCornerShape(HRadius.input))
                .plainClickable(onClick = onAlbum),
            contentAlignment = Alignment.Center,
        ) { HIconView(HIcon.Image, 22.dp) }

        Spacer(Modifier.weight(1f))

        Box(
            Modifier.size(84.dp).dimmed(cameraEnabled).plainClickable(enabled = cameraEnabled, onClick = onCapture),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(80.dp).border(4.dp, HColors.accent, CircleShape))
            Box(Modifier.size(64.dp).background(HColors.accent, CircleShape))
        }

        Spacer(Modifier.weight(1f))

        Box(
            Modifier
                .size(52.dp)
                .dimmed(cameraEnabled)
                .border(1.dp, HColors.border, CircleShape)
                .plainClickable(enabled = cameraEnabled, onClick = onFlip),
            contentAlignment = Alignment.Center,
        ) { HIconView(HIcon.Flip, 22.dp) }
    }
}

private suspend fun cameraProvider(context: Context): ProcessCameraProvider = suspendCoroutine { continuation ->
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({ continuation.resume(future.get()) }, ContextCompat.getMainExecutor(context))
}

/** 카메라가 돌려 찍힌 방향을 바로 세워요. */
private fun ImageProxy.uprightBitmap(): Bitmap {
    val bitmap = toBitmap()
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return bitmap
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/** 앨범 사진 읽기 (방향 정보 반영, 너무 크면 줄여서) */
private fun loadPhoto(context: Context, uri: Uri): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAX_PHOTO_SIDE) decoder.setTargetSampleSize((longest + MAX_PHOTO_SIDE - 1) / MAX_PHOTO_SIDE)
        }
    } else {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }
}.getOrNull()

private const val MAX_PHOTO_SIDE = 3000

/** 가운데를 기준으로 3:4(세로) 로 잘라요. */
private fun Bitmap.croppedToThreeByFour(): Bitmap {
    val target = 3f / 4f
    return if (width.toFloat() / height > target) {
        val w = (height * target).toInt()
        Bitmap.createBitmap(this, (width - w) / 2, 0, w, height)
    } else {
        val h = (width / target).toInt()
        Bitmap.createBitmap(this, 0, (height - h) / 2, width, h)
    }
}
