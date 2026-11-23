package com.sapphire.hawe.feature.photo

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** 사진 속 얼굴 상자를 찾아요(TODO_2/C3). 화면 로직을 가짜 검출기로 테스트할 수 있게 인터페이스로 둬요. */
fun interface FaceDetectorClient {
    /** [bitmap] 픽셀 좌표의 얼굴 상자들. 방향은 이미 바로 세운 비트맵이어야 해요(`loadCroppedPhoto`). */
    suspend fun detect(bitmap: Bitmap): List<PixelRect>
}

/**
 * ML Kit 얼굴 검출(번들 모델, 기기 안에서만). 정확도 우선이고, 단체 사진 뒤쪽의 작은 얼굴도 잡도록
 * 최소 얼굴 크기를 기본(0.1)보다 작게 잡았어요 — 값은 기기 테스트에서 다시 정해요.
 */
class MlKitFaceDetector(private val minFaceSize: Float = MIN_FACE_SIZE) : FaceDetectorClient {
    override suspend fun detect(bitmap: Bitmap): List<PixelRect> {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(minFaceSize)
            .build()
        val detector = FaceDetection.getClient(options)
        try {
            return detector.process(InputImage.fromBitmap(bitmap, 0)).await()
                .map { face -> face.boundingBox.let { PixelRect(it.left, it.top, it.right, it.bottom) } }
        } finally { detector.close() }
    }

    companion object {
        /** 이미지 폭 대비 가장 작은 얼굴 비율 */
        const val MIN_FACE_SIZE = 0.05f
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

/** 검출 결과. [failed] 면 모델을 못 쓰거나 너무 오래 걸린 것 — 직접 영역을 그려 가리도록 안내해요. */
data class FaceDetectOutcome(val state: FaceBlurState, val failed: Boolean)

/** 검출을 이만큼 넘게 기다리면 포기하고 직접 그리기로 넘어가요. */
const val FACE_DETECT_TIMEOUT_MILLIS = 10_000L

/**
 * 사진 크기와 모드로 처음 상태를 만들어요. [BlurMode.OFF] 면 검출하지 않아요.
 * 검출이 실패하거나 시간을 넘기면 예외 대신 빈 상태 + [FaceDetectOutcome.failed] 로 돌려줘서 기능 전체가 죽지 않아요.
 */
suspend fun detectFaceState(width: Int, height: Int, mode: BlurMode,
    timeoutMillis: Long = FACE_DETECT_TIMEOUT_MILLIS, detect: suspend () -> List<PixelRect>): FaceDetectOutcome {
    if (mode == BlurMode.OFF) return FaceDetectOutcome(FaceBlurState(width, height, mode = mode), failed = false)
    val faces = try { withTimeoutOrNull(timeoutMillis) { detect() } }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { null }
    return if (faces == null) FaceDetectOutcome(FaceBlurState(width, height, mode = mode), failed = true)
        else FaceDetectOutcome(FaceBlurState.detected(width, height, faces, mode), failed = false)
}
