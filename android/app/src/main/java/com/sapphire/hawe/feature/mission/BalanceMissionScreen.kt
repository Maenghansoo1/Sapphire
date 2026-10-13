package com.sapphire.hawe.feature.mission

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.data.SubmissionContent
import com.sapphire.hawe.core.model.MissionSpec
import com.sapphire.hawe.core.model.MissionTemplates
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HRadius
import com.sapphire.hawe.designsystem.tokens.HType

/** 밸런스 게임(TEXT_BALANCE): A/B 큰 카드 중 하나 + 이유(선택, 200자). 카드 문구는 서버 빈칸 optionA/B. */
@Composable
fun BalanceMissionScreen(topic: String, spec: MissionSpec, onClose: () -> Unit,
    onSubmit: suspend (SubmissionContent) -> Boolean) {
    var choice by rememberSaveable { mutableStateOf<String?>(null) }
    var reason by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val state = BalanceState(choice, reason.text)
    TemplateMissionScaffold(MissionType.WRITE, topic, templateGuideText(MissionTemplates.TEXT_BALANCE, spec),
        state.hasDraft, state.canSubmit, onClose, { onSubmit(state.content()) }) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BalanceOption("A", spec.optionA, choice == "A") { choice = "A" }
            Text("VS", Modifier.fillMaxWidth(), style = HType.plex(14, FontWeight.Bold),
                color = HColors.textTertiary, textAlign = TextAlign.Center)
            BalanceOption("B", spec.optionB, choice == "B") { choice = "B" }
        }
        Spacer(Modifier.height(24.dp))
        MissionTextBox(reason, { reason = it.applying { text -> reasonInput(text, TemplateLimits.BALANCE_REASON) } },
            "왜 골랐는지 적어도 좋아요", state.counter, label = "이유 (선택)", minHeight = 120.dp)
    }
}

/** 임시 UI: Figma 디자인 확정 시 교체. 고른 카드는 강조색 테두리와 체크로 표시해요. */
@Composable
private fun BalanceOption(letter: String, option: String?, selected: Boolean, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(HRadius.card)
    Row(Modifier.fillMaxWidth().heightIn(min = 84.dp).clip(shape)
        .background(if (selected) HColors.accentSubtle else HColors.bg)
        .border(if (selected) 2.dp else 1.dp, if (selected) HColors.accent else HColors.border, shape)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
        .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(32.dp).background(if (selected) HColors.accent else HColors.surface, CircleShape),
            contentAlignment = Alignment.Center) {
            Text(letter, style = HType.plex(15, FontWeight.Bold), color = if (selected) HColors.onAccent else HColors.ink)
        }
        Text(option?.takeIf { it.isNotBlank() } ?: letter, Modifier.weight(1f),
            style = HType.plex(18, FontWeight.SemiBold, 1.4f), color = HColors.ink)
        if (selected) HIconView(HIcon.CHECK, size = 20.dp, color = HColors.accentPressed)
    }
}
