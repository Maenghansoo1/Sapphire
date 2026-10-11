package com.sapphire.hawe.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class HToastState {
    var message by mutableStateOf<String?>(null)
        private set
    private var generation by mutableStateOf(0)
    fun show(text: String) { message = text; generation++ }
    internal val token: Int get() = generation
    internal fun clear(token: Int) { if (generation == token) message = null }
}

@Composable
fun rememberHToastState(): HToastState = remember { HToastState() }

@Composable
fun BoxScope.HToastHost(state: HToastState, bottomPadding: Dp = 24.dp) {
    val token = state.token
    LaunchedEffect(token) {
        if (state.message != null) {
            delay(1600)
            state.clear(token)
        }
    }
    state.message?.let { message ->
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPadding)) { HToast(message) }
    }
}
