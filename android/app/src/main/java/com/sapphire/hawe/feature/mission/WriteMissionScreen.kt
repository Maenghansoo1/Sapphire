package com.sapphire.hawe.feature.mission

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.HButton
import com.sapphire.hawe.designsystem.HCTAScaffold
import com.sapphire.hawe.designsystem.HColors
import com.sapphire.hawe.designsystem.HLayout
import com.sapphire.hawe.designsystem.HRadius
import com.sapphire.hawe.designsystem.HType
import com.sapphire.hawe.designsystem.plex
import com.sapphire.hawe.model.MissionType
import com.sapphire.hawe.model.SubmissionContent

private const val MAX_LENGTH = 300

/** 08 미션 수행 · 글 (Figma 1:658) — 최대 300자, 글자 수 표시 */
@Composable
fun WriteMissionScreen(topic: String, onClose: () -> Unit, onSubmit: (SubmissionContent) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val trimmed = text.trim()

    MissionScaffold(
        hasDraft = trimmed.isNotEmpty(),
        onClose = onClose,
        trailing = {
            Text("${text.length} / $MAX_LENGTH", style = HType.caption13.copy(fontFeatureSettings = "tnum"), color = HColors.textSecondary)
        },
    ) {
        HCTAScaffold(
            horizontalPadding = HLayout.screenPaddingCompact,
            bottomInset = 32.dp,
            content = {
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    MissionTitle(MissionType.Write, topic)
                    Editor(text) { text = it.take(MAX_LENGTH) }
                }
            },
            cta = { HButton("올리기", enabled = trimmed.isNotEmpty()) { onSubmit(SubmissionContent.Text(trimmed)) } },
        )
    }
}

/** 테두리 line · 모서리 20 · 안쪽 20 · 높이 360 · 17pt 줄 높이 1.7 */
@Composable
private fun Editor(text: String, onChange: (String) -> Unit) {
    val style = plex(17, lineHeight = 1.7f)
    BasicTextField(
        value = text,
        onValueChange = onChange,
        textStyle = style.copy(color = HColors.ink),
        cursorBrush = SolidColor(HColors.ink),
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .border(1.dp, HColors.line, RoundedCornerShape(HRadius.card))
            .padding(20.dp),
        decorationBox = { inner ->
            Box {
                if (text.isEmpty()) Text("여기에 적어보세요", style = style, color = HColors.textTertiary)
                inner()
            }
        },
    )
}
