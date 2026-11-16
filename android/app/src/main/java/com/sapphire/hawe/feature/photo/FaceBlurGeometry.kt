package com.sapphire.hawe.feature.photo

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 화면 좌표(px) 사각형 */
data class ViewRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * 비율을 지켜 화면 가운데에 맞춘(ContentScale.Fit) 이미지와 화면 사이 좌표 변환.
 * 얼굴 검출은 바로 세운 비트맵 기준이라 회전은 따로 없어요(`loadCroppedPhoto` 가 EXIF 방향을 이미 반영).
 */
data class FitViewport(val imageWidth: Int, val imageHeight: Int, val viewWidth: Float, val viewHeight: Float) {
    init { require(imageWidth > 0 && imageHeight > 0) { "이미지 크기는 1 이상이어야 해요" } }

    val scale: Float = if (viewWidth <= 0f || viewHeight <= 0f) 0f else min(viewWidth / imageWidth, viewHeight / imageHeight)
    val offsetX: Float = (viewWidth - imageWidth * scale) / 2
    val offsetY: Float = (viewHeight - imageHeight * scale) / 2

    fun toView(rect: PixelRect) = ViewRect(offsetX + rect.left * scale, offsetY + rect.top * scale,
        offsetX + rect.right * scale, offsetY + rect.bottom * scale)

    /** 화면 점 → 이미지 픽셀(이미지 밖이면 가장자리로) */
    fun toImageX(x: Float): Int = if (scale == 0f) 0 else ((x - offsetX) / scale).roundToInt().coerceIn(0, imageWidth)
    fun toImageY(y: Float): Int = if (scale == 0f) 0 else ((y - offsetY) / scale).roundToInt().coerceIn(0, imageHeight)

    /** 드래그 시작·끝(어느 방향이든) → 이미지 사각형. 이미지 밖으로 끌어도 안으로 잘려요. */
    fun dragToImageRect(startX: Float, startY: Float, endX: Float, endY: Float): PixelRect {
        val x0 = toImageX(startX); val x1 = toImageX(endX)
        val y0 = toImageY(startY); val y1 = toImageY(endY)
        return PixelRect(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1))
    }
}

/** 직접 그린 영역이 이보다 작으면(이미지 px) 실수로 스친 걸로 보고 버려요. */
const val MIN_MANUAL_REGION = 16

/** 작은 얼굴도 누르기 쉽게, 화면 상자를 가운데 기준으로 가로·세로 최소 [minSize] 까지 넓힌 누름 영역 */
fun touchTarget(rect: ViewRect, minSize: Float): ViewRect {
    val padX = max(0f, minSize - rect.width) / 2
    val padY = max(0f, minSize - rect.height) / 2
    return ViewRect(rect.left - padX, rect.top - padY, rect.right + padX, rect.bottom + padY)
}

/**
 * 영역을 그릴 순서. 큰 것부터 그려서 작은 상자가 위에 와요. 위에 있는 게 먼저 눌리므로
 * 겹친 곳을 누르면 **가장 작은 상자**가 눌려요(큰 상자 안의 작은 얼굴을 따로 켜고 끌 수 있게).
 */
fun drawOrder(regions: List<FaceRegion>): List<FaceRegion> =
    regions.sortedWith(compareByDescending<FaceRegion> { it.rect.width.toLong() * it.rect.height }.thenBy { it.id })

/** 사진 아래에 늘 보이는 확인 문구. 자동 검출이 완벽하다고 하지 않아요. */
const val FACE_BLUR_CHECK_HINT = "놓친 얼굴이 없는지 확인해 주세요"

/** 편집 단계 안내 한 줄 */
fun faceBlurGuide(state: FaceBlurState?, detecting: Boolean, failed: Boolean): String {
    if (detecting || state == null) return "얼굴을 찾고 있어요…"
    if (failed && state.regions.isEmpty()) return "얼굴을 자동으로 찾지 못했어요. 가릴 곳을 직접 그려 주세요"
    val found = state.regions.count { it.source == RegionSource.AUTO }
    val hidden = state.selected().size
    return when {
        found == 0 && hidden == 0 -> "찾은 얼굴이 없어요. 필요하면 가릴 곳을 직접 그려 주세요"
        found == 0 -> "직접 그린 ${hidden}곳을 가렸어요"
        else -> "얼굴 ${found}개를 찾았어요 · ${hidden}곳 가림"
    }
}

/** 화면 낭독용. 몇 번째 영역인지, 지금 가렸는지, 누르면 어떻게 되는지 */
fun faceRegionDescription(region: FaceRegion, number: Int): String {
    val kind = if (region.source == RegionSource.AUTO) "얼굴" else "직접 그린 영역"
    return if (region.blurred) "$kind $number, 가림, 누르면 풀어요" else "$kind $number, 안 가림, 누르면 가려요"
}
