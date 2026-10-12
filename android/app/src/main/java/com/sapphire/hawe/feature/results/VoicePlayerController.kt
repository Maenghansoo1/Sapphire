package com.sapphire.hawe.feature.results

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.Submission
import java.io.File

class VoicePlayerController {
    var playingID by mutableStateOf<Long?>(null)
        private set
    var elapsed by mutableDoubleStateOf(0.0)
        private set
    var progress by mutableFloatStateOf(0f)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var preparing: MediaPlayer? = null
    private var current: Submission? = null
    private val queue = ArrayDeque<Submission>()
    private var released = false
    private var generation = 0
    private val ticker = object : Runnable {
        override fun run() {
            current ?: return
            val active = player ?: return
            val duration = active.duration.coerceAtLeast(100) / 1000.0
            elapsed = runCatching { active.currentPosition / 1000.0 }.getOrDefault(0.0)
            progress = voiceProgress(elapsed, duration)
            val finished = runCatching { !active.isPlaying }.getOrDefault(true)
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
        val currentGeneration = generation
        val local = submission.mediaURL?.takeIf { it.scheme == "file" }?.let { runCatching { File(it) }.getOrNull() }
        val url = submission.mediaURL?.takeIf { it.scheme == "http" || it.scheme == "https" }
        if (local?.isFile != true && url == null) { advance(); return }
        val created = MediaPlayer()
        try {
            created.setDataSource(local?.absolutePath ?: url.toString())
            created.setOnCompletionListener { if (!released && generation == currentGeneration) advance() }
            created.setOnErrorListener { _, _, _ ->
                if (!released && generation == currentGeneration) advance()
                true
            }
            if (url != null) {
                preparing = created
                created.setOnPreparedListener {
                    if (released || generation != currentGeneration) return@setOnPreparedListener
                    preparing = null
                    player = created
                    created.start()
                    handler.post(ticker)
                }
                created.prepareAsync()
            } else {
                created.prepare()
                player = created
                created.start()
                handler.post(ticker)
            }
        } catch (_: Exception) { runCatching { created.release() }; advance() }
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
        val pending = preparing
        preparing = null
        runCatching { active?.stop() }
        runCatching { active?.release() }
        runCatching { pending?.reset() }
        runCatching { pending?.release() }
        current = null
        playingID = null
        elapsed = 0.0
        progress = 0f
    }

    fun stop() { stopCurrent(); queue.clear() }
    fun release() { if (!released) { released = true; stop() } }
}
