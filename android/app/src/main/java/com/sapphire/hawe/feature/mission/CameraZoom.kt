package com.sapphire.hawe.feature.mission

import java.util.Locale

/** 사진 미션 카메라의 확대 배율. 기기가 허용하는 범위 안에서 최대 [MAX] 배까지(그 이상은 화질이 너무 떨어져요). */
object CameraZoom {
    const val MAX = 10f

    /** 기기 범위([deviceMin]~[deviceMax])와 [MAX] 안으로 맞춰요. */
    fun clamp(value: Float, deviceMin: Float, deviceMax: Float): Float {
        val upper = minOf(deviceMax, MAX).coerceAtLeast(deviceMin)
        return value.coerceIn(deviceMin, upper)
    }

    /** "1.0×", "2.5×" — 소수 첫째 자리까지 */
    fun label(ratio: Float): String = String.format(Locale.ROOT, "%.1f×", ratio)
}
