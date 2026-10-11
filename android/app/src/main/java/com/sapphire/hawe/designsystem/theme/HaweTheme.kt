package com.sapphire.hawe.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HType

@Composable
fun HaweTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = HColors.accent, background = HColors.bg,
        surface = HColors.bg, onBackground = HColors.ink, onSurface = HColors.ink),
        typography = MaterialTheme.typography.copy(bodyLarge = HType.body15), content = content)
}
