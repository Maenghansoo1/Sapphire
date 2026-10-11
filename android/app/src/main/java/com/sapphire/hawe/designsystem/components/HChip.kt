package com.sapphire.hawe.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HType

@Composable
fun HChip(title: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.defaultMinSize(minWidth = 80.dp, minHeight = 44.dp)
        .background(if (isSelected) HColors.ink else HColors.bg, CircleShape)
        .border(1.dp, if (isSelected) HColors.ink else HColors.border, CircleShape)
        .clickable(onClick = onClick).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
        Text(title, style = HType.plex(15, if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (isSelected) HColors.onAccent else HColors.ink, maxLines = 1)
    }
}
