package com.sapphire.hawe.feature.mission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.data.SubmissionContent
import com.sapphire.hawe.core.model.MissionSpec
import com.sapphire.hawe.core.model.MissionTemplates
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.designsystem.components.HSliderEndLabels
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HRadius
import com.sapphire.hawe.designsystem.tokens.HType

/**
 * 슬라이더 3종(SLIDER_POSITION·SLIDER_TASTE·SLIDER_CONSENSUS): 양 끝 라벨 사이 0~100, 기본 50.
 * 다수결 예측은 "내 위치"와 "그룹 평균 예측" 슬라이더 두 개예요.
 */
@Composable
fun SliderMissionScreen(topic: String, template: String, spec: MissionSpec, onClose: () -> Unit,
    onSubmit: suspend (SubmissionContent) -> Boolean) {
    val consensus = template == MissionTemplates.SLIDER_CONSENSUS
    var number by rememberSaveable { mutableStateOf(TemplateLimits.SLIDER_DEFAULT) }
    var guess by rememberSaveable { mutableStateOf(TemplateLimits.SLIDER_DEFAULT) }
    var touched by rememberSaveable { mutableStateOf(false) }
    val state = SliderState(number, guess, consensus, touched)
    TemplateMissionScaffold(MissionType.SLIDER, topic, templateGuideText(template, spec), state.touched,
        canSubmit = true, onClose = onClose, onSubmit = { onSubmit(state.content()) }) {
        spec.sliderQuestion?.takeIf { it.isNotBlank() && it != topic }?.let {
            Text(it, Modifier.padding(bottom = 12.dp), style = HType.plex(16, FontWeight.SemiBold), color = HColors.ink)
        }
        SliderInput(if (consensus) "내 위치" else null, number, spec) { number = it; touched = true }
        if (consensus) {
            Spacer(Modifier.height(16.dp))
            SliderInput("우리 그룹 평균은 어디쯤일까?", guess, spec) { guess = it; touched = true }
        }
    }
}

/** 임시 UI: Material3 Slider 를 디자인 토큰 색으로 맞췄어요(Figma 확정 시 교체). */
@Composable
private fun SliderInput(label: String?, value: Int, spec: MissionSpec, onChange: (Int) -> Unit) {
    val description = listOfNotNull(label, spec.left?.let { "왼쪽 $it" }, spec.right?.let { "오른쪽 $it" })
        .joinToString(", ")
    Column(Modifier.fillMaxWidth().background(HColors.surface, RoundedCornerShape(HRadius.card))
        .padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.orEmpty(), Modifier.weight(1f), style = HType.fieldLabel, color = HColors.textSecondary)
            Spacer(Modifier.width(12.dp))
            Text(value.toString(), style = HType.plex(26, FontWeight.Bold).copy(fontFeatureSettings = "tnum"),
                color = HColors.ink)
        }
        Slider(value = value.toFloat(), onValueChange = { onChange(sliderValue(it)) },
            valueRange = 0f..TemplateLimits.SLIDER_MAX.toFloat(),
            modifier = Modifier.semantics { if (description.isNotEmpty()) contentDescription = description },
            colors = SliderDefaults.colors(thumbColor = HColors.accent, activeTrackColor = HColors.accent,
                inactiveTrackColor = HColors.disabled))
        HSliderEndLabels(spec.left, spec.right)
    }
}
