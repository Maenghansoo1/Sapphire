package com.sapphire.hawe.core.push

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class PushCenter {
    var pendingDestination by mutableStateOf<PushDestination?>(null)
        private set

    fun handle(intent: Intent?) {
        val destination = parsePushDestination(intent?.getStringExtra("type"),
            intent?.getStringExtra("group_id"), intent?.getStringExtra("submission_id"))
        if (destination != null) pendingDestination = destination
    }

    fun consume() { pendingDestination = null }
}
