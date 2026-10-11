package com.sapphire.hawe.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HType

@Composable
fun HListRow(label: String, modifier: Modifier = Modifier, value: String? = null,
    height: Dp = 52.dp, showsDivider: Boolean = true, showsChevron: Boolean = true, onClick: () -> Unit) {
    Box(modifier.fillMaxWidth().height(height).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = HType.body15, color = HColors.ink, maxLines = 1)
            Spacer(Modifier.weight(1f))
            if (value != null) Text(value, style = HType.plex(14), color = HColors.textSecondary, maxLines = 1)
            if (showsChevron) { Spacer(Modifier.width(6.dp)); HIconView(HIcon.CHEVRON_RIGHT, size = 16.dp, color = HColors.textTertiary) }
        }
        if (showsDivider) Box(Modifier.fillMaxWidth().height(1.dp).align(Alignment.BottomCenter).background(HColors.line))
    }
}
