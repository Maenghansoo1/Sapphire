package com.sapphire.hawe.feature.photo

import org.junit.Assert.*
import org.junit.Test

class FaceBlurStateTest {
    private val faces = listOf(PixelRect(100, 100, 200, 200), PixelRect(400, 100, 460, 160))

    @Test fun detectedFacesStartBlurredInAllMode() {
        val state = FaceBlurState.detected(1000, 800, faces, BlurMode.ALL)
        assertEquals(listOf(1, 2), state.regions.map { it.id })
        assertTrue(state.regions.all { it.blurred && it.source == RegionSource.AUTO })
        assertEquals(PixelRect(85, 85, 215, 215), state.regions[0].rect)  // 15% 넓힘
        assertEquals(state.regions, state.selected())
    }

    @Test fun noneModeFindsButDoesNotBlur() {
        val state = FaceBlurState.detected(1000, 800, faces, BlurMode.NONE)
        assertEquals(2, state.regions.size)
        assertTrue(state.selected().isEmpty())
        assertEquals(listOf(2), state.toggle(2).selected().map { it.id })
    }

    @Test fun offModeSelectsNothingAndIgnoresEdits() {
        val state = FaceBlurState.detected(1000, 800, faces, BlurMode.OFF)
        assertTrue(state.selected().isEmpty())
        assertSame(state, state.toggle(1))
        assertSame(state, state.addManual(PixelRect(0, 0, 50, 50)))
    }

    @Test fun boxesOutsideImageAreDropped() {
        val state = FaceBlurState.detected(300, 300, faces + PixelRect(900, 900, 950, 950), BlurMode.ALL)
        assertEquals(1, state.regions.size)  // 두 번째(400~)와 세 번째는 이미지 밖
        assertEquals(2, state.addManual(PixelRect(10, 10, 20, 20)).regions.last().id)
    }

    @Test fun toggleFlipsOnlyThatFace() {
        val state = FaceBlurState.detected(1000, 800, faces, BlurMode.ALL).toggle(1)
        assertEquals(listOf(false, true), state.regions.map { it.blurred })
        assertEquals(listOf(true, true), state.toggle(1).regions.map { it.blurred })
        assertSame(state, state.toggle(99))
    }

    @Test fun manualRegionsCanBeAddedAndRemoved() {
        val start = FaceBlurState.detected(1000, 800, faces, BlurMode.NONE)
        val added = start.addManual(PixelRect(900, 700, 1100, 900))
        val manual = added.regions.last()
        assertEquals(3, manual.id)
        assertEquals(RegionSource.MANUAL, manual.source)
        assertTrue(manual.blurred)  // 직접 그린 건 가린 채로
        assertEquals(PixelRect(900, 700, 1000, 800), manual.rect)  // 이미지 밖은 잘라요
        assertEquals(listOf(3), added.selected().map { it.id })
        assertEquals(listOf(false), added.toggle(3).regions.filter { it.id == 3 }.map { it.blurred })

        assertSame(added, added.addManual(PixelRect(1200, 0, 1300, 50)))  // 완전히 밖
        assertSame(added, added.addManual(PixelRect(10, 10, 10, 30)))  // 폭 0
        assertSame(added, added.removeManual(1))  // 자동 검출 영역은 지우지 않아요
        val removed = added.removeManual(3)
        assertEquals(listOf(1, 2), removed.regions.map { it.id })
        // 지운 번호는 다시 쓰지 않아요
        assertEquals(4, removed.addManual(PixelRect(0, 0, 30, 30)).regions.last().id)
    }

    @Test fun defaultModeAppliesToEveryRegion() {
        val mixed = FaceBlurState.detected(1000, 800, faces, BlurMode.ALL).toggle(1).addManual(PixelRect(0, 0, 40, 40))
        assertTrue(mixed.applyDefaultMode(BlurMode.ALL).regions.all { it.blurred })
        assertTrue(mixed.applyDefaultMode(BlurMode.NONE).regions.none { it.blurred })
        val off = mixed.applyDefaultMode(BlurMode.OFF)
        assertTrue(off.selected().isEmpty())
        assertEquals(mixed.regions, off.regions)  // 영역은 남겨 둬서 다시 켜면 돌아와요
        assertEquals(3, off.applyDefaultMode(BlurMode.ALL).selected().size)
    }

    @Test fun selectedFeedsMosaic() {
        val width = 64; val height = 64
        val pixels = IntArray(width * height) { (0xFF shl 24) or (it % 251 shl 8) }
        val original = pixels.copyOf()
        val state = FaceBlurState.detected(width, height, listOf(PixelRect(8, 8, 24, 24)), BlurMode.ALL)
            .addManual(PixelRect(40, 40, 56, 56)).toggle(2)
        applyMosaic(pixels, width, height, state.selected())
        assertNotEquals(original[10 * width + 10], pixels[10 * width + 10])  // 자동 영역은 가려짐
        for (y in 40 until 56) for (x in 40 until 56) assertEquals(original[y * width + x], pixels[y * width + x])  // 끈 영역은 그대로
    }

    @Test fun rejectsEmptyImage() {
        assertThrows(IllegalArgumentException::class.java) { FaceBlurState(0, 10) }
    }
}
