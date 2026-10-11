package com.sapphire.hawe.feature.mission

import kotlin.math.floor
import kotlin.math.log10

const val VOICE_BAR_COUNT = 32
const val VOICE_MAX_SECONDS = 30.0

fun levelFromAmplitude(amplitude: Int): Float {
    if (amplitude <= 0) return 0f
    val db = 20 * log10(amplitude.coerceAtMost(32767) / 32767.0)
    return ((db + 50) / 50).coerceIn(0.0, 1.0).toFloat()
}

fun barIndexFor(elapsedSeconds: Double): Int =
    floor(elapsedSeconds.coerceAtLeast(0.0) / VOICE_MAX_SECONDS * VOICE_BAR_COUNT)
        .toInt().coerceIn(0, VOICE_BAR_COUNT - 1)

fun updatedLevels(levels: List<Float>, elapsedSeconds: Double, level: Float): List<Float> {
    val next = levels.toMutableList()
    if (next.isNotEmpty()) {
        val index = barIndexFor(elapsedSeconds).coerceAtMost(next.lastIndex)
        next[index] = maxOf(next[index], level.coerceIn(0f, 1f))
    }
    return next
}

fun voiceTimeText(seconds: Double): String {
    val total = floor(seconds.coerceAtLeast(0.0)).toInt()
    return "%02d:%02d".format(total / 60, total % 60)
}

data class WaveformBarState(val heightDp: Float, val recorded: Boolean, val played: Boolean)

fun waveformBarState(index: Int, count: Int, level: Float, progress: Float?): WaveformBarState =
    WaveformBarState(if (level < 0f) 8f else 8f + 72f * level.coerceIn(0f, 1f),
        level >= 0f, progress == null || index.toFloat() / count.coerceAtLeast(1) < progress)

data class VoiceUiState(val isRecording: Boolean, val hasRecording: Boolean) {
    val controlsEnabled get() = hasRecording && !isRecording
    val canSubmit get() = controlsEnabled
    val hasDraft get() = hasRecording || isRecording
}
