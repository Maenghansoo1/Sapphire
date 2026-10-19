package com.sapphire.hawe.core.dev

import com.sapphire.hawe.core.HaweTime
import java.time.Instant
import java.time.LocalDate

object AppClock {
    fun now(): Instant = Instant.now().plusSeconds(DevMode.offsetSeconds)
    fun today(): LocalDate = now().atZone(HaweTime.SEOUL).toLocalDate()
}
