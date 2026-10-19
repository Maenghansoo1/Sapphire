package com.sapphire.hawe.feature.mission

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraZoomTest {
    @Test fun clampsToDeviceRangeAndTenTimes() {
        assertEquals(1f, CameraZoom.clamp(0.5f, 1f, 8f))
        assertEquals(3f, CameraZoom.clamp(3f, 1f, 8f))
        assertEquals(8f, CameraZoom.clamp(20f, 1f, 8f))
        // 기기가 30배까지 돼도 10배까지만
        assertEquals(10f, CameraZoom.clamp(25f, 1f, 30f))
        // 초광각이 있는 기기는 1배 아래로도 줄일 수 있어요
        assertEquals(0.6f, CameraZoom.clamp(0.3f, 0.6f, 10f))
        // 확대가 안 되는 카메라(최대 1배)
        assertEquals(1f, CameraZoom.clamp(2f, 1f, 1f))
    }

    @Test fun labelHasOneDecimalEvenWithCommaLocale() {
        val locale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1.0×", CameraZoom.label(1f))
            assertEquals("2.5×", CameraZoom.label(2.46f))
            assertEquals("10.0×", CameraZoom.label(10f))
        } finally { Locale.setDefault(locale) }
    }
}
