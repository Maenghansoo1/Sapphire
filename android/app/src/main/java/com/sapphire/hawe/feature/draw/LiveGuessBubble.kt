package com.sapphire.hawe.feature.draw

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.R
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HType

/**
 * 그림판 위 별이 말풍선 (Figma 06-3·06-4·06-5 그림 · 별이 실시간 추측).
 * 왼쪽에 테두리 원 속 별이(40), 오른쪽에 말풍선(높이 52 고정 — 문구가 바뀌어도 그림판이 들썩이지 않아요).
 * 맞히면 말풍선이 accent 로 바뀌고 화면 낭독기가 알려 줘요.
 */
@Composable
fun LiveGuessBubble(state: LiveGuessState, modifier: Modifier = Modifier) {
    val copy = liveGuessCopy(state)
    val celebrate = copy.celebrate
    val bubbleColor by animateColorAsState(if (celebrate) HColors.accent else HColors.surface, label = "liveGuessBubble")
    val avatarColor by animateColorAsState(if (celebrate) HColors.bg else HColors.accentSubtle, label = "liveGuessAvatar")
    Row(modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = copy.description
            if (celebrate) liveRegion = LiveRegionMode.Polite
        },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(40.dp).background(avatarColor, CircleShape).border(1.5.dp, HColors.accent, CircleShape),
            contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.mascot_byeol), null, Modifier.size(28.dp), contentScale = ContentScale.Fit)
        }
        Row(Modifier.weight(1f).height(52.dp)
                .background(bubbleColor, RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                .padding(start = 14.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(copy.title, style = HType.plex(14, FontWeight.SemiBold),
                    color = if (celebrate) HColors.onAccent else HColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                copy.detail?.let {
                    Text(it, style = HType.caption12.copy(fontFeatureSettings = "tnum"),
                        color = if (celebrate) HColors.onAccent.copy(alpha = 0.85f) else HColors.textSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (copy.candidates.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    copy.candidates.forEachIndexed { index, guess -> CandidateChip(guess, leading = index == 0) }
                }
            }
            copy.badge?.let {
                Text(it, Modifier.background(HColors.bg, CircleShape).padding(horizontal = 9.dp, vertical = 4.dp),
                    style = HType.plex(12, FontWeight.SemiBold), color = HColors.accentPressed, maxLines = 1)
            }
        }
    }
}

/** 후보 칩 — 1등은 accentSubtle 바탕, 2등은 흰 바탕 */
@Composable
private fun CandidateChip(guess: AiGuess, leading: Boolean) {
    val color = if (leading) HColors.accentPressed else HColors.textSecondary
    Row(Modifier.background(if (leading) HColors.accentSubtle else HColors.bg, CircleShape)
            .padding(horizontal = 7.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(guess.category.ko, style = HType.plex(12, FontWeight.Medium), color = color, maxLines = 1)
        Text(percentText(guess.probability), style = HType.caption12.copy(fontFeatureSettings = "tnum"), color = color)
    }
}
