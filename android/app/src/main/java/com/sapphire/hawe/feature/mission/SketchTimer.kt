package com.sapphire.hawe.feature.mission

import java.time.Clock
import java.time.Duration
import java.time.Instant

class SketchTimer(val totalSeconds: Int, private val clock: Clock = Clock.systemUTC()) {
    private var startedAt: Instant? = null
    val started: Boolean get() = startedAt != null
    fun start() { if (startedAt == null) startedAt = Instant.now(clock) }
    fun remainingSeconds(): Int {
        val start = startedAt ?: return totalSeconds
        val elapsed = Duration.between(start, Instant.now(clock)).seconds.coerceAtLeast(0)
        return (totalSeconds - elapsed).coerceAtLeast(0L).toInt()
    }
    val expired: Boolean get() = started && remainingSeconds() == 0
}
