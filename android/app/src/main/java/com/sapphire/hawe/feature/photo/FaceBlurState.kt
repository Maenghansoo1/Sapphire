package com.sapphire.hawe.feature.photo

/** 영역을 어떻게 얻었는지. 자동 검출 영역은 켜고 끄기만, 직접 그린 영역은 지울 수도 있어요. */
enum class RegionSource { AUTO, MANUAL }

/** 모자이크할 수 있는 영역 하나. [rect] 는 원본 비트맵 픽셀 기준이고 이미지 안으로 잘려 있어요. */
data class FaceRegion(val id: Int, val rect: PixelRect, val source: RegionSource, val blurred: Boolean)

/**
 * 블러 기본 모드(AI_FEATURES.md §6 결정 3).
 * - [ALL]: 찾은 얼굴을 모두 가린 채로 시작
 * - [NONE]: 찾기만 하고 사용자가 누른 얼굴만 가림
 * - [OFF]: 기능을 끔 — 아무것도 가리지 않고 고를 수도 없어요
 */
enum class BlurMode { ALL, NONE, OFF }

/**
 * 사용자가 얼굴마다 모자이크를 켜고 끄는 상태(TODO_2/C2). 바뀔 때마다 새 값을 돌려주는 불변 값이라 Compose 상태로 그대로 쓸 수 있어요.
 * [selected] 가 실제로 모자이크할 영역이에요([applyBlur] 에 넘겨요).
 */
data class FaceBlurState(
    val imageWidth: Int,
    val imageHeight: Int,
    val regions: List<FaceRegion> = emptyList(),
    val mode: BlurMode = BlurMode.ALL,
    private val nextId: Int = 1,
) {
    init { require(imageWidth > 0 && imageHeight > 0) { "이미지 크기는 1 이상이어야 해요" } }

    /** 실제로 모자이크할 영역. [BlurMode.OFF] 면 비어 있어요. */
    fun selected(): List<FaceRegion> = if (mode == BlurMode.OFF) emptyList() else regions.filter { it.blurred }

    /** 얼굴 하나를 켜거나 꺼요. 없는 번호거나 [BlurMode.OFF] 면 그대로예요. */
    fun toggle(id: Int): FaceBlurState {
        if (mode == BlurMode.OFF || regions.none { it.id == id }) return this
        return copy(regions = regions.map { if (it.id == id) it.copy(blurred = !it.blurred) else it })
    }

    /** 사용자가 직접 그린 영역을 가린 채로 추가해요. 이미지 밖으로 나간 부분은 잘라 내고, 남는 게 없거나 [BlurMode.OFF] 면 그대로예요. */
    fun addManual(rect: PixelRect): FaceBlurState {
        val area = rect.clampTo(imageWidth, imageHeight)
        if (mode == BlurMode.OFF || area.isEmpty) return this
        return copy(regions = regions + FaceRegion(nextId, area, RegionSource.MANUAL, blurred = true), nextId = nextId + 1)
    }

    /** 직접 그린 영역을 지워요. 자동 검출 영역은 지우지 않고(끄기만 가능) 그대로 둬요. */
    fun removeManual(id: Int): FaceBlurState {
        if (regions.none { it.id == id && it.source == RegionSource.MANUAL }) return this
        return copy(regions = regions.filterNot { it.id == id })
    }

    /** 모드를 바꾸고 모든 영역에 그 기본값을 적용해요. [BlurMode.OFF] 는 영역을 지우지 않고 고른 목록만 비워서, 다시 켜면 돌아와요. */
    fun applyDefaultMode(mode: BlurMode): FaceBlurState = when (mode) {
        BlurMode.ALL -> copy(mode = mode, regions = regions.map { it.copy(blurred = true) })
        BlurMode.NONE -> copy(mode = mode, regions = regions.map { it.copy(blurred = false) })
        BlurMode.OFF -> copy(mode = mode)
    }

    companion object {
        /**
         * 얼굴 검출 결과로 시작해요. 상자는 [DEFAULT_PADDING_RATIO] 만큼 넓히고 이미지 안으로 잘라요(남는 게 없으면 버려요).
         * [mode] 가 [BlurMode.ALL] 이면 모두 가린 채로, 아니면 모두 연 채로 시작해요.
         */
        fun detected(imageWidth: Int, imageHeight: Int, faces: List<PixelRect>, mode: BlurMode): FaceBlurState {
            val areas = faces.map { expand(it, DEFAULT_PADDING_RATIO, imageWidth, imageHeight) }.filterNot { it.isEmpty }
            val regions = areas.mapIndexed { index, area -> FaceRegion(index + 1, area, RegionSource.AUTO, mode == BlurMode.ALL) }
            return FaceBlurState(imageWidth, imageHeight, regions, mode, regions.size + 1)
        }
    }
}
