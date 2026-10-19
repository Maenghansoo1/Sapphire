package com.sapphire.hawe.core.dev

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object DevMode {
    private var preferences: android.content.SharedPreferences? = null
    var enabled by mutableStateOf(false)
        private set
    var offsetSeconds by mutableLongState()
        private set

    fun init(context: Context) {
        if (preferences != null) return
        preferences = context.applicationContext.getSharedPreferences("hawe_dev", Context.MODE_PRIVATE)
        enabled = preferences?.getBoolean("enabled", false) == true
        offsetSeconds = if (enabled) preferences?.getLong("clockOffsetSeconds", 0) ?: 0 else 0
    }

    fun changeEnabled(value: Boolean) {
        enabled = value
        if (!value) offsetSeconds = 0
        preferences?.edit()?.putBoolean("enabled", value)?.putLong("clockOffsetSeconds", offsetSeconds)?.apply()
    }

    fun setOffset(seconds: Long) {
        if (!enabled) return
        offsetSeconds = seconds
        preferences?.edit()?.putLong("clockOffsetSeconds", seconds)?.apply()
    }
}

private fun mutableLongState() = mutableStateOf(0L)
