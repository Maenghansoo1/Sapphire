package com.sapphire.hawe.feature.mission

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import java.io.File

class VoiceRecorderController(context: Context) {
    val file = File(context.cacheDir, "mission-voice.m4a")
    var isRecording by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var hasRecording by mutableStateOf(false)
        private set
    var elapsed by mutableDoubleStateOf(0.0)
        private set
    var levels by mutableStateOf(List(VOICE_BAR_COUNT) { -1f })
        private set
    var playbackProgress by mutableFloatStateOf(0f)
        private set

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var startedAtMs = 0L
    private var released = false
    private val ticker = object : Runnable {
        override fun run() {
            when {
                isRecording -> {
                    elapsed = ((SystemClock.elapsedRealtime() - startedAtMs) / 1000.0).coerceAtMost(VOICE_MAX_SECONDS)
                    val amplitude = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                    levels = updatedLevels(levels, elapsed, levelFromAmplitude(amplitude))
                    if (elapsed >= VOICE_MAX_SECONDS) stop() else handler.postDelayed(this, 100)
                }
                isPlaying -> {
                    val active = player
                    playbackProgress = runCatching {
                        (active!!.currentPosition / active.duration.coerceAtLeast(100).toFloat()).coerceIn(0f, 1f)
                    }.getOrDefault(0f)
                    handler.postDelayed(this, 100)
                }
            }
        }
    }

    fun start() {
        if (released || isRecording) return
        reset()
        val created = try {
            if (Build.VERSION.SDK_INT >= 31) MediaRecorder(appContext)
            else { @Suppress("DEPRECATION") MediaRecorder() }
        } catch (_: Exception) { return }
        try {
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setAudioChannels(1)
            created.setAudioSamplingRate(44_100)
            created.setAudioEncodingBitRate(128_000)
            created.setMaxDuration(30_000)
            created.setOutputFile(file.absolutePath)
            created.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) handler.post { stop() }
            }
            created.prepare()
            created.start()
            recorder = created
            startedAtMs = SystemClock.elapsedRealtime()
            isRecording = true
            handler.postDelayed(ticker, 100)
        } catch (_: Exception) {
            runCatching { created.release() }
            recorder = null
            isRecording = false
            file.delete()
        }
    }

    fun stop() {
        if (!isRecording) return
        handler.removeCallbacks(ticker)
        elapsed = ((SystemClock.elapsedRealtime() - startedAtMs) / 1000.0).coerceAtMost(VOICE_MAX_SECONDS)
        if (elapsed >= VOICE_MAX_SECONDS) levels = updatedLevels(levels, VOICE_MAX_SECONDS, 0f)
        val active = recorder
        recorder = null
        isRecording = false
        val stopped = runCatching { active?.stop() }.isSuccess
        runCatching { active?.release() }
        hasRecording = file.exists() && file.length() > 0 && elapsed > 0 && (stopped || elapsed >= 29.9)
        if (!hasRecording) { file.delete(); elapsed = 0.0; levels = List(VOICE_BAR_COUNT) { -1f } }
    }

    fun togglePlayback() {
        if (released || isRecording) return
        if (isPlaying) { stopPlayback(); return }
        if (!hasRecording) return
        val created = MediaPlayer()
        try {
            created.setDataSource(file.absolutePath)
            created.prepare()
            created.setOnCompletionListener { stopPlayback() }
            created.start()
            player = created
            isPlaying = true
            handler.post(ticker)
        } catch (_: Exception) {
            runCatching { created.release() }
            player = null
            isPlaying = false
        }
    }

    fun stopPlayback() {
        if (!isPlaying && player == null) return
        handler.removeCallbacks(ticker)
        val active = player
        player = null
        isPlaying = false
        playbackProgress = 0f
        runCatching { active?.stop() }
        runCatching { active?.release() }
    }

    fun onBackground() { if (isRecording) stop(); if (isPlaying) stopPlayback() }

    fun reset() {
        handler.removeCallbacks(ticker)
        if (isRecording) {
            runCatching { recorder?.stop() }
            isRecording = false
        }
        runCatching { recorder?.release() }
        recorder = null
        stopPlayback()
        hasRecording = false
        elapsed = 0.0
        levels = List(VOICE_BAR_COUNT) { -1f }
        file.delete()
    }

    fun release() { if (!released) { released = true; reset() } }
}
