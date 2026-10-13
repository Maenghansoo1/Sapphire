package com.sapphire.hawe.feature.draw

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.sapphire.hawe.feature.mission.DrawStroke
import com.sapphire.hawe.feature.mission.drawStrokes
import kotlin.math.roundToInt

/** 잉크 판정에 쓸 ARGB 픽셀(행 우선). 메모리에서만 쓰고 파일·로그에 남기지 않아요. */
class InkImage(val argb: IntArray, val width: Int, val height: Int) {
    /** 명세 (1)~(7). 너무 비었으면 null. */
    fun modelInput(): FloatArray? = InkPreprocessor.preprocess(InkPreprocessor.inkMask(argb, width, height), width, height)
}

private fun Bitmap.toInkImage(): InkImage {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    return InkImage(pixels, width, height)
}

/**
 * 내가 지금 그린 획 → 투명 배경 + 검정 잉크(색은 무시, 지우개는 지운 채로) — 명세 (1) "내가 방금 그린 그림".
 * 해상도는 그림판 dp 그대로(배율 1)라 충분히 작고, 비율 기반인 전처리 결과에는 영향이 없어요.
 */
fun strokeInkImage(strokes: List<DrawStroke>, widthDp: Float, heightDp: Float): InkImage {
    val width = widthDp.roundToInt().coerceAtLeast(1)
    val height = heightDp.roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    return try {
        drawStrokes(Canvas(bitmap), strokes.map { if (it.isEraser) it else it.copy(color = android.graphics.Color.BLACK) }, 1f)
        bitmap.toInkImage()
    } finally { bitmap.recycle() }
}

/** 결과 화면의 그림 PNG(흰 배경 + 색 잉크) → 픽셀. 화면에 이미 띄운 이미지라 보통 Coil 캐시에서 바로 와요. 실패하면 null. */
suspend fun loadInkImage(context: Context, model: Any?): InkImage? {
    model ?: return null
    val request = ImageRequest.Builder(context).data(model).allowHardware(false).build()
    val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
    return result.image.toBitmap().toInkImage()
}
