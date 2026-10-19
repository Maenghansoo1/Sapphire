package com.sapphire.hawe.feature.mission

import android.annotation.SuppressLint
import android.content.Context
import android.view.ScaleGestureDetector
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.UUID

class PhotoCameraController(val previewView: PreviewView) {
    var ready by mutableStateOf(false)
    var front by mutableStateOf(false)
    var flash by mutableStateOf(false)
    var capturing by mutableStateOf(false)
    var canFlip by mutableStateOf(false)
    /** 지금 확대 배율과 이 카메라가 허용하는 범위. 찍은 사진에도 같은 배율이 들어가요. */
    var zoom by mutableFloatStateOf(1f)
    var minZoom by mutableFloatStateOf(1f)
    var maxZoom by mutableFloatStateOf(1f)
    internal var camera: Camera? = null
    internal var capture: ImageCapture? = null
    internal var provider: ProcessCameraProvider? = null

    fun toggleFlash() {
        flash = !flash
        runCatching { capture?.flashMode = if (flash && !front) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF }
    }

    fun flip() { if (ready && canFlip) front = !front }

    fun zoomTo(value: Float) {
        val active = camera ?: return
        zoom = CameraZoom.clamp(value, minZoom, maxZoom)
        runCatching { active.cameraControl.setZoomRatio(zoom) }
    }

    /** 두 손가락으로 벌리거나 오므린 비율만큼 */
    fun zoomBy(scale: Float) = zoomTo(zoom * scale)

    fun takePicture(context: Context, onPhoto: (File) -> Unit) {
        val active = capture ?: return
        if (!ready || capturing) return
        capturing = true
        val file = File(context.cacheDir, "capture-${UUID.randomUUID()}.jpg")
        active.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    capturing = false
                    runCatching { onPhoto(file) }.onFailure { file.delete() }
                }
                override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                    capturing = false
                    file.delete()
                }
            })
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun rememberPhotoCameraController(enabled: Boolean): PhotoCameraController {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller = remember {
        PhotoCameraController(PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }).also { controller ->
            // 두 손가락 핀치로 확대·축소
            val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    controller.zoomBy(detector.scaleFactor)
                    return true
                }
            })
            controller.previewView.setOnTouchListener { _, event -> pinch.onTouchEvent(event); true }
        }
    }
    val front = controller.front
    DisposableEffect(context, owner, enabled, front) {
        if (!enabled) {
            controller.ready = false
            onDispose { }
        } else {
            var disposed = false
            val future = ProcessCameraProvider.getInstance(context)
            val executor = ContextCompat.getMainExecutor(context)
            future.addListener({
                if (!disposed) runCatching {
                    val provider = future.get()
                    val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    val other = if (front) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                    if (!provider.hasCamera(selector)) {
                        controller.ready = false
                        if (provider.hasCamera(other)) controller.front = !front
                    } else {
                        provider.unbindAll()
                        val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
                        val capture = ImageCapture.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                        capture.flashMode = if (controller.flash && !front) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                        preview.surfaceProvider = controller.previewView.surfaceProvider
                        val camera = provider.bindToLifecycle(owner, selector, preview, capture)
                        val zoomState = camera.cameraInfo.zoomState.value
                        controller.camera = camera
                        controller.minZoom = zoomState?.minZoomRatio ?: 1f
                        controller.maxZoom = zoomState?.maxZoomRatio ?: 1f
                        // 카메라를 새로 열면(앞·뒤 전환 포함) 1배부터
                        controller.zoomTo(1f)
                        controller.provider = provider
                        controller.capture = capture
                        controller.canFlip = provider.hasCamera(other)
                        controller.ready = true
                    }
                }.onFailure { controller.ready = false }
            }, executor)
            onDispose {
                disposed = true
                controller.ready = false
                controller.capture = null
                controller.camera = null
                controller.zoom = 1f
                controller.provider?.unbindAll()
                controller.provider = null
            }
        }
    }
    return controller
}

@Composable
fun CameraPreviewView(controller: PhotoCameraController) {
    AndroidView(factory = { controller.previewView }, modifier = Modifier.fillMaxSize())
}
