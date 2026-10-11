package com.sapphire.hawe.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HLayout
import com.sapphire.hawe.designsystem.tokens.HType

enum class HNavLeading { NONE, BACK, CLOSE }

@Composable
fun HNavBar(title: String? = null, leading: HNavLeading = HNavLeading.NONE,
            onLeading: () -> Unit = {}, titleStyle: TextStyle = HType.navTitle,
            trailing: @Composable () -> Unit = {}) {
    var trailingWidthPx by remember { mutableIntStateOf(0) }
    val trailingPadding = with(LocalDensity.current) { trailingWidthPx.toDp() + 8.dp }
    Box(Modifier.fillMaxWidth().height(HLayout.headerHeight), contentAlignment = Alignment.Center) {
        if (title != null) Text(title,
            modifier = Modifier.fillMaxWidth().padding(horizontal = maxOf(HLayout.minTouch + 8.dp, trailingPadding)),
            style = titleStyle, color = HColors.ink, textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(HLayout.minTouch).then(if (leading != HNavLeading.NONE) Modifier.clickable(onClick = onLeading) else Modifier),
                contentAlignment = Alignment.CenterStart) {
                if (leading != HNavLeading.NONE) HIconView(if (leading == HNavLeading.BACK) HIcon.CHEVRON_LEFT else HIcon.CLOSE)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.widthIn(min = HLayout.minTouch).height(HLayout.minTouch)
                .onSizeChanged { trailingWidthPx = it.width }, contentAlignment = Alignment.CenterEnd) { trailing() }
        }
    }
}

@Composable
fun HNavStep(text: String) { Text(text, style = HType.plex(14, androidx.compose.ui.text.font.FontWeight.SemiBold), color = HColors.textSecondary) }
