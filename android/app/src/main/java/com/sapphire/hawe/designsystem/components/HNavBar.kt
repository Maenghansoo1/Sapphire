package com.sapphire.hawe.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HLayout
import com.sapphire.hawe.designsystem.tokens.HType

enum class HNavLeading { NONE, BACK, CLOSE }

@Composable
fun HNavBar(title: String? = null, leading: HNavLeading = HNavLeading.NONE,
            onLeading: () -> Unit = {}, trailing: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxWidth().height(HLayout.headerHeight), contentAlignment = Alignment.Center) {
        if (title != null) Text(title, style = HType.navTitle, color = HColors.ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(HLayout.minTouch).then(if (leading != HNavLeading.NONE) Modifier.clickable(onClick = onLeading) else Modifier),
                contentAlignment = Alignment.CenterStart) {
                if (leading != HNavLeading.NONE) HIconView(if (leading == HNavLeading.BACK) HIcon.CHEVRON_LEFT else HIcon.CLOSE)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(HLayout.minTouch).height(HLayout.minTouch), contentAlignment = Alignment.CenterEnd) { trailing() }
        }
    }
}

@Composable
fun HNavStep(text: String) { Text(text, style = HType.plex(14, androidx.compose.ui.text.font.FontWeight.SemiBold), color = HColors.textSecondary) }
