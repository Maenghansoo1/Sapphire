package com.sapphire.hawe.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HType

@Composable
fun HToast(message: String) {
    Box(Modifier.defaultMinSize(minHeight = 44.dp).background(HColors.ink.copy(alpha = 0.9f), CircleShape)
        .padding(horizontal = 18.dp, vertical = 11.dp)) {
        Text(message, style = HType.plex(14, FontWeight.SemiBold), color = HColors.onAccent)
    }
}
