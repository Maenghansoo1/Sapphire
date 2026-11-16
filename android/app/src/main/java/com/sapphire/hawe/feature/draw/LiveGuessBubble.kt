package com.sapphire.hawe.feature.draw

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HRadius
import com.sapphire.hawe.designsystem.tokens.HType

/**
 * 그림판 위 AI 말풍선(임시 UI: Figma 확정 시 교체, 아이콘도 임시 `AI_GUESS_TEMP`).
 * 높이를 고정해서 문구가 바뀌어도 그림판이 들썩이지 않아요. 맞히면 배경색이 바뀌고 화면 낭독기가 알려 줘요.
 */
@Composable
fun LiveGuessBubble(state: LiveGuessState, judged: Boolean, modifier: Modifier = Modifier) {
    val copy = liveGuessCopy(state, judged)
    val background by animateColorAsState(if (copy.celebrate) HColors.accentSubtle else HColors.surface,
        label = "liveGuessBackground")
    Row(modifier.fillMaxWidth().height(52.dp).background(background, RoundedCornerShape(HRadius.card))
        .clearAndSetSemantics {
            contentDescription = copy.description
            if (copy.celebrate) liveRegion = LiveRegionMode.Polite
        }
        .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HIconView(HIcon.AI_GUESS_TEMP, size = 22.dp, color = if (copy.celebrate) HColors.accentPressed else HColors.textSecondary)
        Column(Modifier.weight(1f)) {
            Text(copy.title, style = HType.plex(15, FontWeight.SemiBold),
                color = if (copy.celebrate) HColors.accentPressed else HColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            copy.detail?.let {
                Text(it, style = HType.caption12.copy(fontFeatureSettings = "tnum"), color = HColors.textSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        copy.badge?.let {
            Text(it, Modifier.background(HColors.bg, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
                style = HType.caption12, color = HColors.accentPressed, maxLines = 1)
        }
    }
}
