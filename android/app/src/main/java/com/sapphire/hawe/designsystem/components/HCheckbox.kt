package com.sapphire.hawe.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HRadius

@Composable
fun HCheckboxMark(isOn: Boolean) {
    val shape = RoundedCornerShape(HRadius.checkbox)
    Box(Modifier.size(22.dp).background(if (isOn) HColors.ink else HColors.bg, shape)
        .border(1.5.dp, if (isOn) HColors.ink else HColors.controlBorder, shape), contentAlignment = Alignment.Center) {
        if (isOn) HIconView(HIcon.CHECK_SMALL, size = 14.dp, color = HColors.onAccent)
    }
}

@Composable
fun HCheckbox(isOn: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier,
              label: @Composable () -> Unit = {}) {
    Row(modifier.defaultMinSize(minHeight = 44.dp).clickable { onChange(!isOn) }, verticalAlignment = Alignment.CenterVertically) {
        HCheckboxMark(isOn)
        Spacer(Modifier.width(10.dp))
        label()
    }
}
