package com.sapphire.hawe.feature.mission

import android.content.Context
import androidx.camera.core.AspectRatio
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
    internal var capture: ImageCapture? = null
    internal var provider: ProcessCameraProvider? = null

    fun toggleFlash() {
        flash = !flash
        runCatching { capture?.flashMode = if (flash && !front) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF }
    }

    fun flip() { if (ready && canFlip) front = !front }

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

@Composable
fun rememberPhotoCameraController(enabled: Boolean): PhotoCameraController {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller = remember { PhotoCameraController(PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }) }
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
                        provider.bindToLifecycle(owner, selector, preview, capture)
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
