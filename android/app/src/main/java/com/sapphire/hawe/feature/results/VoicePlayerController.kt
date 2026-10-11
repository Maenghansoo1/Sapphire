package com.sapphire.hawe.feature.results

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.Submission
import java.io.File
import java.util.UUID

class VoicePlayerController {
    var playingID by mutableStateOf<UUID?>(null)
        private set
    var elapsed by mutableDoubleStateOf(0.0)
        private set
    var progress by mutableFloatStateOf(0f)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var current: Submission? = null
    private val queue = ArrayDeque<Submission>()
    private var startedAtMs = 0L
    private var released = false
    private var generation = 0
    private val ticker = object : Runnable {
        override fun run() {
            val item = current ?: return
            val active = player
            val duration = if (active != null) active.duration.coerceAtLeast(100) / 1000.0 else (item.duration ?: 1.0).coerceAtLeast(0.1)
            elapsed = if (active != null) runCatching { active.currentPosition / 1000.0 }.getOrDefault(0.0)
                else (SystemClock.elapsedRealtime() - startedAtMs) / 1000.0
            progress = voiceProgress(elapsed, duration)
            val finished = if (active != null) runCatching { !active.isPlaying }.getOrDefault(true)
                else elapsed >= duration
            if (finished || progress >= 1f) advance() else handler.postDelayed(this, 100)
        }
    }

    fun toggle(submission: Submission) {
        if (released) return
        if (playingID == submission.id) stop()
        else { queue.clear(); play(submission) }
    }

    fun playAll(submissions: List<Submission>) {
        if (released) return
        val voices = submissions.filter { it.type == MissionType.VOICE }
        if (voices.isEmpty()) { stop(); return }
        queue.clear()
        queue.addAll(voices.drop(1))
        play(voices.first())
    }

    private fun play(submission: Submission) {
        stopCurrent()
        if (released) return
        current = submission
        playingID = submission.id
        startedAtMs = SystemClock.elapsedRealtime()
        val local = submission.mediaURL?.takeIf { it.scheme == "file" }?.let { runCatching { File(it) }.getOrNull() }
        if (local?.isFile == true) {
            val created = MediaPlayer()
            try {
                created.setDataSource(local.absolutePath)
                created.prepare()
                val currentGeneration = generation
                created.setOnCompletionListener { if (!released && generation == currentGeneration) advance() }
                created.start()
                player = created
            } catch (_: Exception) { runCatching { created.release() } }
        }
        handler.post(ticker)
    }

    private fun advance() {
        if (released) return
        val next = if (queue.isEmpty()) null else queue.removeFirst()
        if (next == null) stop() else play(next)
    }

    private fun stopCurrent() {
        handler.removeCallbacks(ticker)
        generation++
        val active = player
        player = null
        runCatching { active?.stop() }
        runCatching { active?.release() }
        current = null
        playingID = null
        elapsed = 0.0
        progress = 0f
    }

    fun stop() { stopCurrent(); queue.clear() }
    fun release() { if (!released) { released = true; stop() } }
}
