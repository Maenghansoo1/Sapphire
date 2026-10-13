package com.sapphire.hawe.feature.mission

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.data.SubmissionContent
import com.sapphire.hawe.core.model.MissionSpec
import com.sapphire.hawe.core.model.MissionTemplates
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HType

/** 한 줄 후기(TEXT_REVIEW): 별점 1~5(★/☆ 텍스트 기호 — 임시 UI) + 한 줄(100자). */
@Composable
fun ReviewMissionScreen(topic: String, onClose: () -> Unit, onSubmit: suspend (SubmissionContent) -> Boolean) {
    var stars by rememberSaveable { mutableStateOf(0) }
    var line by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val state = ReviewState(stars, line.text)
    TemplateMissionScaffold(MissionType.WRITE, topic,
        templateGuideText(MissionTemplates.TEXT_REVIEW, MissionSpec()), state.hasDraft, state.canSubmit,
        onClose, { onSubmit(state.content()) }) {
        Text("별점", style = HType.fieldLabel, color = HColors.textSecondary)
        Row(Modifier.padding(top = 4.dp).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
            (1..TemplateLimits.STARS).forEach { count ->
                Box(Modifier.size(52.dp).clip(CircleShape)
                    .selectable(selected = stars == count, role = Role.RadioButton) { stars = count }
                    .semantics { contentDescription = "별 ${count}개" }, contentAlignment = Alignment.Center) {
                    Text(if (count <= stars) "★" else "☆", Modifier.clearAndSetSemantics { },
                        style = HType.plex(34), color = if (count <= stars) HColors.accent else HColors.controlBorder)
                }
            }
        }
        Text(if (stars == 0) "별점을 골라 주세요" else "${stars}점", style = HType.plex(14, FontWeight.SemiBold),
            color = if (stars == 0) HColors.textSecondary else HColors.ink)
        Spacer(Modifier.height(24.dp))
        MissionTextBox(line, { line = it.applying(::reviewInput) }, "한 줄 후기를 남겨요", state.counter, label = "한 줄 후기",
            minHeight = 96.dp)
    }
}
