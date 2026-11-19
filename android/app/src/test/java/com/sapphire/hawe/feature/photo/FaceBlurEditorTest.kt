package com.sapphire.hawe.feature.photo

import com.sapphire.hawe.core.model.MissionTemplates
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FaceBlurEditorTest {
    // ---- 좌표 변환 ----

    @Test fun fitViewportCentersAndScales() {
        // 이미지 1500×2000(3:4)을 화면 300×300 에 맞추면 높이 기준 0.15배, 좌우 여백 (300 - 225) / 2
        val viewport = FitViewport(1500, 2000, 300f, 300f)
        assertEquals(0.15f, viewport.scale, 1e-6f)
        assertEquals(37.5f, viewport.offsetX, 1e-4f)
        assertEquals(0f, viewport.offsetY, 1e-4f)
        val view = viewport.toView(PixelRect(100, 200, 300, 400))
        assertEquals(37.5f + 15f, view.left, 1e-3f)
        assertEquals(30f, view.top, 1e-3f)
        assertEquals(37.5f + 45f, view.right, 1e-3f)
        assertEquals(60f, view.bottom, 1e-3f)
        assertEquals(100, viewport.toImageX(37.5f + 15f))
        assertEquals(400, viewport.toImageY(60f))
        assertEquals(0, viewport.toImageX(0f))  // 여백을 눌러도 이미지 가장자리로
        assertEquals(1500, viewport.toImageX(299f))
    }

    @Test fun dragInAnyDirectionGivesSameRect() {
        val viewport = FitViewport(1000, 1000, 500f, 500f)
        val forward = viewport.dragToImageRect(50f, 60f, 150f, 260f)
        assertEquals(PixelRect(100, 120, 300, 520), forward)
        assertEquals(forward, viewport.dragToImageRect(150f, 260f, 50f, 60f))
        assertEquals(PixelRect(0, 0, 1000, 1000), viewport.dragToImageRect(-50f, -50f, 900f, 900f))  // 밖으로 끌어도 안으로
    }

    @Test fun zeroSizedViewDoesNotCrash() {
        val viewport = FitViewport(100, 100, 0f, 0f)
        assertEquals(0f, viewport.scale)
        assertEquals(0, viewport.toImageX(10f))
    }

    // ---- 누름 영역·겹침 ----

    @Test fun tinyBoxesGetMinimumTouchTarget() {
        val target = touchTarget(ViewRect(100f, 100f, 110f, 120f), minSize = 44f)
        assertEquals(44f, target.width, 1e-4f)
        assertEquals(44f, target.height, 1e-4f)
        assertEquals(105f, (target.left + target.right) / 2, 1e-4f)  // 가운데는 그대로
        val big = ViewRect(0f, 0f, 100f, 80f)
        assertEquals(big, touchTarget(big, 44f))
    }

    @Test fun smallerOverlappingBoxIsOnTop() {
        val big = FaceRegion(1, PixelRect(0, 0, 400, 400), RegionSource.AUTO, true)
        val small = FaceRegion(2, PixelRect(100, 100, 150, 150), RegionSource.AUTO, true)
        val manual = FaceRegion(3, PixelRect(0, 0, 200, 200), RegionSource.MANUAL, true)
        // 큰 것부터 그려서 마지막(맨 위, 먼저 눌림)이 가장 작은 상자
        assertEquals(listOf(1, 3, 2), drawOrder(listOf(small, big, manual)).map { it.id })
        val twin = FaceRegion(4, PixelRect(500, 500, 550, 550), RegionSource.AUTO, false)
        assertEquals(listOf(2, 4), drawOrder(listOf(twin, small)).map { it.id })  // 크기가 같으면 번호 순
    }

    // ---- 틀별 기본값·설정 ----

    @Test fun recommendedModeBlursGalleryPhotosOnly() {
        assertEquals(BlurMode.ALL, blurModeFor(MissionTemplates.PHOTO_GALLERY, BlurChoice.AUTO))
        assertEquals(BlurMode.NONE, blurModeFor(MissionTemplates.PHOTO_NOW, BlurChoice.AUTO))
        assertEquals(BlurMode.NONE, blurModeFor(MissionTemplates.PHOTO_COLOR, BlurChoice.AUTO))
        assertEquals(BlurMode.ALL, blurModeFor(MissionTemplates.PHOTO_NOW, BlurChoice.ALL))
        assertEquals(BlurMode.NONE, blurModeFor(MissionTemplates.PHOTO_GALLERY, BlurChoice.NONE))
        assertEquals(BlurMode.OFF, blurModeFor(MissionTemplates.PHOTO_GALLERY, BlurChoice.OFF))
    }

    @Test fun storedChoiceFallsBackToRecommended() {
        assertEquals(BlurChoice.OFF, parseBlurChoice("OFF"))
        assertEquals(BlurChoice.AUTO, parseBlurChoice(null))
        assertEquals(BlurChoice.AUTO, parseBlurChoice("SOMETHING_OLD"))
    }

    // ---- 검출(가짜 검출기) ----

    @Test fun detectedFacesFollowMode() = runTest {
        val faces = listOf(PixelRect(100, 100, 200, 200))
        val all = detectFaceState(1000, 1000, BlurMode.ALL) { faces }
        assertFalse(all.failed)
        assertEquals(1, all.state.selected().size)
        val none = detectFaceState(1000, 1000, BlurMode.NONE) { faces }
        assertEquals(1, none.state.regions.size)
        assertTrue(none.state.selected().isEmpty())
    }

    @Test fun offModeSkipsDetection() = runTest {
        var called = false
        val off = detectFaceState(1000, 1000, BlurMode.OFF) { called = true; emptyList() }
        assertFalse(called)
        assertFalse(off.failed)
        assertTrue(off.state.selected().isEmpty())
    }

    @Test fun failureOrTimeoutFallsBackToManual() = runTest {
        val crashed = detectFaceState(1000, 1000, BlurMode.ALL) { error("모델을 못 불러옴") }
        assertTrue(crashed.failed)
        assertTrue(crashed.state.regions.isEmpty())
        // 빈 상태에서도 직접 그려서 가릴 수 있어요
        assertEquals(1, crashed.state.addManual(PixelRect(10, 10, 200, 200)).selected().size)

        val slow = detectFaceState(1000, 1000, BlurMode.ALL, timeoutMillis = 100) { delay(1_000); listOf(PixelRect(0, 0, 10, 10)) }
        assertTrue(slow.failed)
    }

    // ---- 안내 문구 ----

    @Test fun guideNeverPromisesPerfectDetection() {
        assertEquals("얼굴을 찾고 있어요…", faceBlurGuide(null, detecting = true, failed = false))
        val empty = FaceBlurState(100, 100)
        assertEquals("얼굴을 자동으로 찾지 못했어요. 가릴 곳을 직접 그려 주세요", faceBlurGuide(empty, false, failed = true))
        assertEquals("찾은 얼굴이 없어요. 필요하면 가릴 곳을 직접 그려 주세요", faceBlurGuide(empty, false, false))
        val found = FaceBlurState.detected(1000, 1000, listOf(PixelRect(0, 0, 100, 100), PixelRect(300, 300, 400, 400)), BlurMode.ALL)
        assertEquals("얼굴 2개를 찾았어요 · 2곳 가림", faceBlurGuide(found, false, false))
        assertEquals("얼굴 2개를 찾았어요 · 1곳 가림", faceBlurGuide(found.toggle(1), false, false))
        assertEquals("직접 그린 1곳을 가렸어요", faceBlurGuide(empty.addManual(PixelRect(0, 0, 50, 50)), false, failed = true))
        assertEquals("놓친 얼굴이 없는지 확인해 주세요", FACE_BLUR_CHECK_HINT)
    }

    @Test fun regionDescriptionsSayStateAndAction() {
        assertEquals("얼굴 1, 가림, 누르면 풀어요",
            faceRegionDescription(FaceRegion(1, PixelRect(0, 0, 1, 1), RegionSource.AUTO, true), 1))
        assertEquals("직접 그린 영역 3, 안 가림, 누르면 가려요",
            faceRegionDescription(FaceRegion(7, PixelRect(0, 0, 1, 1), RegionSource.MANUAL, false), 3))
    }
}
