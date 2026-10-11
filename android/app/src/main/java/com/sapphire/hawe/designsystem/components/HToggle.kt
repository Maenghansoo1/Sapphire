package com.sapphire.hawe.designsystem.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.tokens.HColors

@Composable
fun HToggle(isOn: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val knobX by animateDpAsState(if (isOn) 23.dp else 3.dp, tween(200), label = "toggle knob")
    Box(modifier.alpha(if (enabled) 1f else 0.4f).size(52.dp, 44.dp)
        .clickable(enabled = enabled) { onChange(!isOn) }) {
        Box(Modifier.offset(y = 6.dp).size(52.dp, 32.dp).background(if (isOn) HColors.ink else HColors.controlOff, CircleShape))
        Box(Modifier.offset(x = knobX, y = 9.dp).size(26.dp).background(Color.White, CircleShape))
    }
}
