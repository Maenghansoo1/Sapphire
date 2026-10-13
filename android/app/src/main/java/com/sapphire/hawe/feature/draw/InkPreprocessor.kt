package com.sapphire.hawe.feature.draw

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * 그림 → 모델 입력(28×28) 전처리. `ml/quickdraw/PREPROCESS_SPEC.md`(버전 1)와 학습 코드(`rasterize.py`)를
 * 그대로 옮겼어요. 하나라도 다르면 정확도만 조용히 떨어지니, 고칠 때는 골든 테스트(`InkPreprocessorTest`)로 확인해요.
 * Bitmap 을 쓰지 않는 순수 계산이라 단위 테스트에서 바로 돌아가요.
 */
object InkPreprocessor {
    const val OUTPUT_SIZE = 28
    const val BOX_SIZE = 20
    const val MARGIN = (OUTPUT_SIZE - BOX_SIZE) / 2
    const val INK_MIN_ALPHA = 128
    const val INK_MAX_CHANNEL = 235

    /** 잉크 칸 수가 이보다 적으면 "너무 비어 있음"(명세 (2)). */
    fun minInk(width: Int, height: Int): Int = max(3, ((width.toLong() * height + 1999) / 2000).toInt())

    /** ARGB 픽셀(Android `Bitmap.getPixels` 순서, 행 우선) → 잉크 마스크(명세 (1)). */
    fun inkMask(argb: IntArray, width: Int, height: Int): BooleanArray {
        require(argb.size == width * height) { "픽셀 수가 크기와 달라요" }
        return BooleanArray(argb.size) { index ->
            val pixel = argb[index]
            val alpha = pixel ushr 24
            val channel = min(pixel shr 16 and 0xFF, min(pixel shr 8 and 0xFF, pixel and 0xFF))
            alpha >= INK_MIN_ALPHA && channel < INK_MAX_CHANNEL
        }
    }

    /** 잉크 마스크(행 우선 w×h) → 28×28 실수(행 우선, 0~1). 너무 비었으면 null(추론하지 않음). */
    fun preprocess(mask: BooleanArray, width: Int, height: Int): FloatArray? {
        require(mask.size == width * height) { "마스크 크기가 달라요" }
        if (mask.count { it } < minInk(width, height)) return null
        // (3) 잉크 경계 상자
        var x0 = width; var x1 = -1; var y0 = height; var y1 = -1
        for (y in 0 until height) for (x in 0 until width) if (mask[y * width + x]) {
            if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
        }
        val bw = x1 - x0 + 1
        val bh = y1 - y0 + 1
        // (4) 정사각형 가운데: square[y][x] = mask[y0 + y − oy][x0 + x − ox]
        val side = max(bw, bh)
        val ox = (side - bw) / 2
        val oy = (side - bh) / 2
        fun square(y: Int, x: Int): Double {
            val sy = y - oy; val sx = x - ox
            return if (sy in 0 until bh && sx in 0 until bw && mask[(y0 + sy) * width + x0 + sx]) 1.0 else 0.0
        }
        // (5) 20×20 면적 평균: small = W · square · Wᵀ (W 는 칸마다 겹치는 원본 칸과 가중치만 들고 있음)
        val weights = areaWeights(side, BOX_SIZE)
        val columns = Array(side) { y -> DoubleArray(BOX_SIZE) { j -> weights[j].sum { x, w -> w * square(y, x) } } }
        val small = Array(BOX_SIZE) { i -> DoubleArray(BOX_SIZE) { j -> weights[i].sum { y, w -> w * columns[y][j] } } }
        // (6) 가장 진한 값을 1로
        val peak = small.maxOf { row -> row.max() }
        // (7) 28×28 가운데(여백 4)
        val out = FloatArray(OUTPUT_SIZE * OUTPUT_SIZE)
        for (i in 0 until BOX_SIZE) for (j in 0 until BOX_SIZE) {
            val value = if (peak > 0) small[i][j] / peak else small[i][j]
            out[(MARGIN + i) * OUTPUT_SIZE + MARGIN + j] = value.toFloat()
        }
        return out
    }

    /** 결과 칸 i 가 원본 구간 [i·src/dst, (i+1)·src/dst) 와 겹치는 원본 칸과 비율(한 칸의 합 = 1). */
    internal fun areaWeights(src: Int, dst: Int): Array<List<Pair<Int, Double>>> {
        val scale = src.toDouble() / dst
        return Array(dst) { i ->
            val start = i.toDouble() * src / dst
            val end = (i + 1).toDouble() * src / dst
            buildList {
                var k = floor(start).toInt()
                while (k < src && k < end) {
                    val overlap = min(end, k + 1.0) - max(start, k.toDouble())
                    if (overlap > 0) add(k to overlap / scale)
                    k++
                }
            }
        }
    }

    private inline fun List<Pair<Int, Double>>.sum(term: (Int, Double) -> Double): Double {
        var total = 0.0
        for ((index, weight) in this) total += term(index, weight)
        return total
    }
}
