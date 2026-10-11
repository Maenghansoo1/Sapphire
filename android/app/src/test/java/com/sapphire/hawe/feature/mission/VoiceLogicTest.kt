package com.sapphire.hawe.feature.mission

import org.junit.Assert.*
import org.junit.Test

class VoiceLogicTest {
    @Test fun amplitudeAndIndices() {
        assertEquals(0f, levelFromAmplitude(0))
        assertEquals(0f, levelFromAmplitude(-3))
        assertEquals(1f, levelFromAmplitude(32767))
        assertEquals(0f, levelFromAmplitude(100))
        assertEquals(0.5f, levelFromAmplitude(1843), 0.03f)
        assertEquals(0, barIndexFor(0.0))
        assertEquals(16, barIndexFor(15.0))
        assertEquals(31, barIndexFor(29.99))
        assertEquals(31, barIndexFor(30.0))
        assertEquals(31, barIndexFor(100.0))
    }

    @Test fun levelsAndTime() {
        val blank = List(VOICE_BAR_COUNT) { -1f }
        val first = updatedLevels(blank, 0.0, 0.3f)
        val second = updatedLevels(first, 0.1, 0.1f)
        assertEquals(0.3f, second[0])
        assertEquals(-1f, second[1])
        assertEquals(listOf(0.3f), second.filter { it >= 0f })
        assertEquals("00:00", voiceTimeText(0.0))
        assertEquals("00:05", voiceTimeText(5.9))
        assertEquals("00:59", voiceTimeText(59.99))
        assertEquals("01:01", voiceTimeText(61.0))
        assertEquals("00:30", voiceTimeText(30.0))
    }

    @Test fun waveformAndControls() {
        assertEquals(WaveformBarState(8f, false, true), waveformBarState(0, 32, -1f, null))
        assertEquals(WaveformBarState(44f, true, true), waveformBarState(8, 32, 0.5f, null))
        assertFalse(waveformBarState(20, 32, 0.5f, 0.5f).played)
        assertTrue(waveformBarState(8, 32, 0.5f, 0.5f).played)
        assertFalse(VoiceUiState(false, false).hasDraft)
        assertFalse(VoiceUiState(false, false).canSubmit)
        assertTrue(VoiceUiState(true, false).hasDraft)
        assertFalse(VoiceUiState(true, false).controlsEnabled)
        assertTrue(VoiceUiState(false, true).controlsEnabled)
        assertTrue(VoiceUiState(false, true).canSubmit)
    }
}
