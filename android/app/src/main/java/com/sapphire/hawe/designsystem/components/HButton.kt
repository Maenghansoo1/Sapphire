package com.sapphire.hawe.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HLayout
import com.sapphire.hawe.designsystem.tokens.HRadius
import com.sapphire.hawe.designsystem.tokens.HType

enum class HButtonKind { PRIMARY, SECONDARY, GRAY, DANGER, DISABLED }

@Composable
fun HButton(title: String, modifier: Modifier = Modifier, kind: HButtonKind = HButtonKind.PRIMARY,
            height: Dp = HLayout.ctaHeight, enabled: Boolean = true, onClick: () -> Unit) {
    val current = if (enabled) kind else HButtonKind.DISABLED
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = when (current) {
        HButtonKind.PRIMARY -> if (pressed) HColors.accentPressed else HColors.accent
        HButtonKind.SECONDARY -> if (pressed) HColors.surfaceStrong else HColors.bg
        HButtonKind.GRAY -> if (pressed) HColors.disabled else HColors.surfaceStrong
        HButtonKind.DANGER -> if (pressed) HColors.danger.copy(alpha = 0.85f) else HColors.danger
        HButtonKind.DISABLED -> HColors.disabled
    }
    val fg = when (current) {
        HButtonKind.PRIMARY, HButtonKind.DANGER -> HColors.onAccent
        HButtonKind.DISABLED -> HColors.textSecondary
        else -> HColors.ink
    }
    val shape = RoundedCornerShape(HRadius.button)
    Box(modifier.fillMaxWidth().height(height).background(bg, shape)
        .then(if (current == HButtonKind.SECONDARY) Modifier.border(1.dp, HColors.border, shape) else Modifier)
        .clickable(interactionSource = interaction, indication = null, enabled = current != HButtonKind.DISABLED, onClick = onClick)
        .padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        Text(title, style = HType.plex(17, androidx.compose.ui.text.font.FontWeight.SemiBold), color = fg,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
