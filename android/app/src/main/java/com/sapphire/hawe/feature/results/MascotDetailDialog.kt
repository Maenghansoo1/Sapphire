package com.sapphire.hawe.feature.results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sapphire.hawe.core.model.AiDetail
import com.sapphire.hawe.designsystem.components.HButton
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HType
import com.sapphire.hawe.feature.draw.percentText

@Composable
fun MascotDetailDialog(detail: AiDetail, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(HColors.bg, RoundedCornerShape(24.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (detail) {
                is AiDetail.PhotoScore -> {
                    Text("별이가 생각한 점수", style = HType.plex(20, FontWeight.Bold), color = HColors.ink)
                    Text("${detail.score}점", style = HType.plex(32, FontWeight.Bold), color = HColors.accentPressed)
                    Text("별이의 점수는 재미용이에요.", style = HType.caption13, color = HColors.textSecondary)
                }
                is AiDetail.DrawGuess -> {
                    Text("별이는 이렇게 생각했어요!", style = HType.plex(20, FontWeight.Bold), color = HColors.ink)
                    detail.guesses.forEachIndexed { index, guess ->
                        val probability = guess.probability.coerceIn(0f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${index + 1}  ${guess.label}", style = HType.plex(15, FontWeight.Medium), color = HColors.ink)
                                Text(percentText(probability), style = HType.caption13, color = HColors.textSecondary)
                            }
                            Box(Modifier.fillMaxWidth().height(8.dp).background(HColors.surfaceStrong, CircleShape)) {
                                if (probability > 0f) Box(Modifier.fillMaxWidth(probability).fillMaxHeight().background(HColors.accent, CircleShape))
                            }
                        }
                    }
                    Text("별이의 추측은 재미용이에요.", style = HType.caption13, color = HColors.textSecondary)
                }
            }
            HButton("닫기", height = 48.dp, onClick = onDismiss)
        }
    }
}
