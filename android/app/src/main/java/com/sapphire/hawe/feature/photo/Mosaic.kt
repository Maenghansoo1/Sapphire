package com.sapphire.hawe.feature.photo

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 얼굴 모자이크(TODO_2/C2). 가우시안 블러는 약하면 되돌릴 여지가 있어서 **픽셀화(블록 평균)** 만 써요.
 * 핵심 계산은 `IntArray`(ARGB, 행 우선 — `Bitmap.getPixels` 순서)로 해서 JVM 테스트로 확인하고, Bitmap 은 [applyBlur] 에만 둬요.
 * 원본 픽셀은 메모리에서만 쓰고 파일·로그에 남기지 않아요.
 */

/** 블록 한 변의 최소 크기(px). 사용자가 모자이크를 이보다 약하게 만들 수 없어요. */
const val MIN_BLOCK_SIZE = 8

/** 얼굴 검출 상자는 얼굴에 딱 붙어서, 네 변을 상자 크기의 이 비율만큼 넓혀요(머리카락·귀까지). */
const val DEFAULT_PADDING_RATIO = 0.15f

/** 픽셀 좌표 사각형. [right]·[bottom] 은 포함하지 않아요(마지막 픽셀 + 1). */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    /** (0, 0)~([imageWidth], [imageHeight]) 안으로 자른 사각형. 완전히 밖이면 [isEmpty] 가 true 예요. */
    fun clampTo(imageWidth: Int, imageHeight: Int) = PixelRect(left.coerceIn(0, imageWidth), top.coerceIn(0, imageHeight),
        right.coerceIn(0, imageWidth), bottom.coerceIn(0, imageHeight))
}

/** 얼굴 폭의 약 1/8, 최소 [MIN_BLOCK_SIZE]. 폭이 좁으면 상자 전체가 한 블록이 될 수 있어요. */
fun blockSizeFor(rect: PixelRect): Int = max(MIN_BLOCK_SIZE, rect.width / 8)

/** 네 변을 각각 상자 폭·높이의 [ratio] 만큼 넓히고 이미지 안으로 잘라요. */
fun expand(rect: PixelRect, ratio: Float, imageWidth: Int, imageHeight: Int): PixelRect {
    require(ratio >= 0f) { "ratio 는 0 이상이어야 해요" }
    val dx = (rect.width.coerceAtLeast(0) * ratio).roundToInt()
    val dy = (rect.height.coerceAtLeast(0) * ratio).roundToInt()
    return PixelRect(rect.left - dx, rect.top - dy, rect.right + dx, rect.bottom + dy).clampTo(imageWidth, imageHeight)
}

/**
 * [rect] 안을 [blockSize] 칸 블록마다 평균색으로 채워요(제자리 변경). 상자 밖 픽셀은 건드리지 않아요.
 * - 블록은 상자 왼쪽 위부터 나누고, 끝에 남는 블록은 남은 크기만큼만 평균해요.
 * - 알파는 픽셀마다 그대로 두고 색만 평균해요(투명한 픽셀 색이 섞이지 않게 알파로 가중).
 * - [blockSize] 가 [MIN_BLOCK_SIZE] 보다 작으면 최소값으로 올려요. 상자가 이미지 밖이면 잘라서 처리하고, 남는 게 없으면 그대로 둬요.
 */
fun pixelate(pixels: IntArray, width: Int, height: Int, rect: PixelRect, blockSize: Int) {
    require(width > 0 && height > 0 && pixels.size == width * height) { "픽셀 수가 크기와 달라요" }
    val area = rect.clampTo(width, height)
    if (area.isEmpty) return
    val block = max(blockSize, MIN_BLOCK_SIZE)
    var y0 = area.top
    while (y0 < area.bottom) {
        val y1 = minOf(y0 + block, area.bottom)
        var x0 = area.left
        while (x0 < area.right) {
            val x1 = minOf(x0 + block, area.right)
            fillAverage(pixels, width, x0, y0, x1, y1)
            x0 = x1
        }
        y0 = y1
    }
}

private fun fillAverage(pixels: IntArray, width: Int, x0: Int, y0: Int, x1: Int, y1: Int) {
    var weight = 0L; var r = 0L; var g = 0L; var b = 0L
    var count = 0L; var plainR = 0L; var plainG = 0L; var plainB = 0L
    for (y in y0 until y1) for (x in x0 until x1) {
        val pixel = pixels[y * width + x]
        val alpha = (pixel ushr 24).toLong()
        val red = (pixel shr 16 and 0xFF).toLong(); val green = (pixel shr 8 and 0xFF).toLong(); val blue = (pixel and 0xFF).toLong()
        weight += alpha; r += red * alpha; g += green * alpha; b += blue * alpha
        count++; plainR += red; plainG += green; plainB += blue
    }
    // 전부 투명하면 알파 가중을 쓸 수 없어서 그냥 평균
    val (red, green, blue) = if (weight > 0) Triple(rounded(r, weight), rounded(g, weight), rounded(b, weight))
        else Triple(rounded(plainR, count), rounded(plainG, count), rounded(plainB, count))
    val color = (red shl 16) or (green shl 8) or blue
    for (y in y0 until y1) for (x in x0 until x1) {
        val index = y * width + x
        pixels[index] = (pixels[index] and 0xFF000000.toInt()) or color
    }
}

private fun rounded(sum: Long, count: Long): Int = ((sum + count / 2) / count).toInt()

/** 고른 영역마다 [blockSizeFor] 크기로 모자이크해요(제자리 변경). 겹치는 영역은 두 번 처리돼도 괜찮아요. */
fun applyMosaic(pixels: IntArray, width: Int, height: Int, regions: List<FaceRegion>) {
    for (region in regions) pixelate(pixels, width, height, region.rect, blockSizeFor(region.rect))
}

/**
 * 고른 영역을 모자이크한 **새** 비트맵. 입력은 바꾸지 않아서 취소하면 원본으로 돌아갈 수 있어요.
 * 고른 영역이 없으면 원본을 그대로 돌려줘요(다시 인코딩할지는 호출자가 정해요).
 * 업로드할 이미지는 이 결과처럼 픽셀 자체가 바뀐 것이어야 해요(위에 덮개만 그린 이미지 금지).
 * 메모리: 픽셀 배열이 가로×세로×4바이트라, 호출자가 긴 변 2048px 정도로 줄여서 넘겨 주세요(약 16MB).
 */
fun applyBlur(bitmap: Bitmap, regions: List<FaceRegion>): Bitmap {
    if (regions.isEmpty()) return bitmap
    val source = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    if (source !== bitmap) source.recycle()
    applyMosaic(pixels, width, height, regions)
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { setPixels(pixels, 0, width, 0, 0, width, height) }
}
