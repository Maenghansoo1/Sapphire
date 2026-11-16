package com.sapphire.hawe.feature.photo

import org.junit.Assert.*
import org.junit.Test

class MosaicTest {
    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    /** 칸마다 색이 다른 이미지(얼굴 대신) */
    private fun pattern(width: Int, height: Int) =
        IntArray(width * height) { i -> val x = i % width; val y = i / width; argb(255, x * 7 % 256, y * 5 % 256, (x * y) % 256) }

    private fun IntArray.at(width: Int, x: Int, y: Int) = this[y * width + x]

    private fun blockColors(pixels: IntArray, width: Int, x0: Int, y0: Int, x1: Int, y1: Int) =
        (y0 until y1).flatMap { y -> (x0 until x1).map { x -> pixels.at(width, x, y) } }.toSet()

    private fun average(pixels: IntArray, width: Int, x0: Int, y0: Int, x1: Int, y1: Int): Int {
        val block = (y0 until y1).flatMap { y -> (x0 until x1).map { x -> pixels.at(width, x, y) } }
        fun channel(shift: Int) = ((block.sumOf { (it shr shift and 0xFF).toLong() } + block.size / 2) / block.size).toInt()
        return argb(255, channel(16), channel(8), channel(0))
    }

    @Test fun pixelateChangesOnlyInsideBox() {
        val width = 32; val height = 32
        val original = pattern(width, height)
        val pixels = original.copyOf()
        pixelate(pixels, width, height, PixelRect(4, 4, 20, 20), blockSize = 8)
        for (y in 0 until height) for (x in 0 until width) {
            val inside = x in 4 until 20 && y in 4 until 20
            if (!inside) assertEquals("($x, $y) 은 상자 밖이라 그대로여야 해요", original.at(width, x, y), pixels.at(width, x, y))
        }
        // 8×8 블록마다 한 색(평균), 원래는 64가지 색
        assertEquals(64, blockColors(original, width, 4, 4, 12, 12).size)
        assertEquals(setOf(average(original, width, 4, 4, 12, 12)), blockColors(pixels, width, 4, 4, 12, 12))
        assertEquals(setOf(average(original, width, 12, 12, 20, 20)), blockColors(pixels, width, 12, 12, 20, 20))
    }

    @Test fun remainderBlocksAverageOnlyWhatIsLeft() {
        // 상자 폭 20, 블록 8 → 8 + 8 + 4
        val width = 24; val height = 8
        val original = pattern(width, height)
        val pixels = original.copyOf()
        pixelate(pixels, width, height, PixelRect(0, 0, 20, 8), blockSize = 8)
        assertEquals(setOf(average(original, width, 16, 0, 20, 8)), blockColors(pixels, width, 16, 0, 20, 8))
        assertNotEquals(pixels.at(width, 15, 0), pixels.at(width, 16, 0))
        for (y in 0 until height) for (x in 20 until width) assertEquals(original.at(width, x, y), pixels.at(width, x, y))
    }

    @Test fun alphaIsKeptAndTransparentColorDoesNotBleed() {
        val pixels = intArrayOf(argb(255, 255, 0, 0), argb(0, 0, 0, 255))  // 빨강(불투명) + 파랑(투명)
        pixelate(pixels, 2, 1, PixelRect(0, 0, 2, 1), MIN_BLOCK_SIZE)
        assertEquals(argb(255, 255, 0, 0), pixels[0])
        assertEquals(argb(0, 255, 0, 0), pixels[1])  // 알파 0 은 그대로, 색만 평균(빨강)

        val half = intArrayOf(argb(128, 200, 0, 0), argb(64, 0, 0, 200))
        pixelate(half, 2, 1, PixelRect(0, 0, 2, 1), MIN_BLOCK_SIZE)
        assertEquals(listOf(128, 64), half.map { it ushr 24 })
        assertEquals(1, half.map { it and 0xFFFFFF }.toSet().size)

        val clear = intArrayOf(argb(0, 100, 0, 0), argb(0, 0, 0, 100))  // 전부 투명하면 그냥 평균
        pixelate(clear, 2, 1, PixelRect(0, 0, 2, 1), MIN_BLOCK_SIZE)
        assertEquals(listOf(argb(0, 50, 0, 50), argb(0, 50, 0, 50)), clear.toList())
    }

    @Test fun blockCannotBeSmallerThanMinimum() {
        val width = 16; val height = 16
        val original = pattern(width, height)
        val pixels = original.copyOf()
        pixelate(pixels, width, height, PixelRect(0, 0, 16, 16), blockSize = 2)  // 2px 로 약하게 하려 해도 8px
        assertEquals(setOf(average(original, width, 0, 0, 8, 8)), blockColors(pixels, width, 0, 0, 8, 8))
        assertEquals(4, blockColors(pixels, width, 0, 0, 16, 16).size)
    }

    @Test fun tinyFaceBecomesOneBlock() {
        val width = 20; val height = 20
        val original = pattern(width, height)
        val pixels = original.copyOf()
        pixelate(pixels, width, height, PixelRect(3, 3, 8, 8), blockSizeFor(PixelRect(3, 3, 8, 8)))
        assertEquals(setOf(average(original, width, 3, 3, 8, 8)), blockColors(pixels, width, 3, 3, 8, 8))
    }

    @Test fun boxesOutsideImageAreClampedOrIgnored() {
        val width = 16; val height = 16
        val original = pattern(width, height)
        val pixels = original.copyOf()
        pixelate(pixels, width, height, PixelRect(20, 20, 40, 40), 8)  // 완전히 밖
        pixelate(pixels, width, height, PixelRect(10, 10, 5, 5), 8)  // 크기가 음수
        assertArrayEquals(original, pixels)
        pixelate(pixels, width, height, PixelRect(-4, -4, 4, 4), 8)  // 일부만 안 → (0,0)~(4,4)
        assertEquals(setOf(average(original, width, 0, 0, 4, 4)), blockColors(pixels, width, 0, 0, 4, 4))
        assertEquals(original.at(width, 4, 4), pixels.at(width, 4, 4))
    }

    @Test fun overlappingRegionsDoNotCrash() {
        val width = 40; val height = 40
        val pixels = pattern(width, height)
        val regions = listOf(FaceRegion(1, PixelRect(0, 0, 30, 30), RegionSource.AUTO, true),
            FaceRegion(2, PixelRect(10, 10, 40, 40), RegionSource.MANUAL, true))
        applyMosaic(pixels, width, height, regions)
        assertEquals(pixels.at(width, 39, 39), pixels.at(width, 36, 36))  // 두 번째 영역의 마지막 블록
    }

    @Test fun rejectsMismatchedSize() {
        assertThrows(IllegalArgumentException::class.java) { pixelate(IntArray(10), 4, 4, PixelRect(0, 0, 2, 2), 8) }
        assertThrows(IllegalArgumentException::class.java) { expand(PixelRect(0, 0, 2, 2), -0.1f, 4, 4) }
    }

    @Test fun blockSizeIsAboutAnEighthOfFaceWidth() {
        assertEquals(MIN_BLOCK_SIZE, blockSizeFor(PixelRect(0, 0, 5, 5)))
        assertEquals(MIN_BLOCK_SIZE, blockSizeFor(PixelRect(0, 0, 40, 40)))
        assertEquals(10, blockSizeFor(PixelRect(0, 0, 80, 80)))
        assertEquals(25, blockSizeFor(PixelRect(100, 100, 300, 300)))
    }

    @Test fun expandAddsPaddingAndClamps() {
        assertEquals(PixelRect(85, 91, 215, 169), expand(PixelRect(100, 100, 200, 160), 0.15f, 1000, 1000))
        assertEquals(PixelRect(0, 0, 58, 58), expand(PixelRect(0, 0, 50, 50), 0.15f, 60, 60))
        assertTrue(expand(PixelRect(100, 100, 120, 120), 0.15f, 60, 60).isEmpty)
        assertEquals(PixelRect(1, 2, 3, 4), expand(PixelRect(1, 2, 3, 4), 0f, 10, 10))
    }
}
