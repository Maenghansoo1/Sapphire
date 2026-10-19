package com.sapphire.hawe.feature.results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.model.AiReview
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HType

/**
 * AI 심사 배지·한 줄 코멘트(B10, 임시 UI: Figma 확정 시 교체). 점수는 숨기고, 배지 없이 격려 코멘트만 보여 줄 수도 있어요.
 * 피드에서는 코멘트 1줄, 상세에서는 2줄까지.
 */
@Composable
fun AiReviewLine(review: AiReview, modifier: Modifier = Modifier, commentLines: Int = 1) {
    val label = aiBadgeLabel(review.badge)
    if (label == null && review.comment == null) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Row(Modifier.background(HColors.accentSubtle, CircleShape)
            .clearAndSetSemantics { contentDescription = label }
            .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            HIconView(HIcon.BADGE_FIT_TEMP, size = 16.dp, color = HColors.accentPressed)
            Text(label, style = HType.plex(13, FontWeight.SemiBold), color = HColors.accentPressed)
        }
        review.comment?.let {
            Text(it, style = HType.caption13, color = HColors.textSecondary, maxLines = commentLines,
                overflow = TextOverflow.Ellipsis)
        }
    }
}
