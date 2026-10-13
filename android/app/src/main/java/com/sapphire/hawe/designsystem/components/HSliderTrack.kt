package com.sapphire.hawe.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HType

/** 읽기 전용 슬라이더 막대 위의 점 하나. [value] 는 0~100. */
data class HSliderMark(val value: Int, val color: Color, val size: Dp = 14.dp)

/**
 * 읽기 전용 슬라이더 막대(임시 UI: Figma 디자인 확정 시 교체).
 * 0~100 위치에 점을 찍고, [average] 가 있으면 그 자리에 세로선을 그어요. 화면 읽기용 설명은 부르는 쪽에서 붙여요.
 */
@Composable
fun HSliderTrack(marks: List<HSliderMark>, modifier: Modifier = Modifier, average: Double? = null) {
    val largest = marks.maxOfOrNull { it.size } ?: 14.dp
    Canvas(modifier.fillMaxWidth().height(largest + 8.dp)) {
        val radius = largest.toPx() / 2
        val start = radius
        val width = (size.width - radius * 2).coerceAtLeast(1f)
        val trackHeight = 6.dp.toPx()
        val centerY = size.height / 2
        fun x(value: Double) = start + width * (value.coerceIn(0.0, 100.0) / 100.0).toFloat()
        drawRoundRect(HColors.disabled, Offset(start, centerY - trackHeight / 2), Size(width, trackHeight),
            CornerRadius(trackHeight / 2))
        average?.let {
            drawLine(HColors.ink, Offset(x(it), 0f), Offset(x(it), size.height), strokeWidth = 2.dp.toPx())
        }
        marks.forEach { mark ->
            val center = Offset(x(mark.value.toDouble()), centerY)
            drawCircle(HColors.bg, mark.size.toPx() / 2 + 1.5.dp.toPx(), center)
            drawCircle(mark.color, mark.size.toPx() / 2, center)
        }
    }
}

/** 슬라이더 양 끝 라벨. 길면 두 줄까지 보이고 말줄임해요. */
@Composable
fun HSliderEndLabels(left: String?, right: String?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        Text(left.orEmpty(), Modifier.weight(1f), style = HType.caption13, color = HColors.textSecondary,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(12.dp))
        Text(right.orEmpty(), Modifier.weight(1f), style = HType.caption13, color = HColors.textSecondary,
            textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
