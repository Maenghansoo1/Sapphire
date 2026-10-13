package com.sapphire.hawe.feature.mission

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.TodayMission
import com.sapphire.hawe.core.model.supportsTemplate
import com.sapphire.hawe.designsystem.components.HAlert
import com.sapphire.hawe.designsystem.components.HButton
import com.sapphire.hawe.designsystem.components.HNavBar
import com.sapphire.hawe.designsystem.components.HNavLeading
import com.sapphire.hawe.designsystem.layout.HCtaScaffold
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HRadius
import com.sapphire.hawe.designsystem.tokens.HType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

fun isPerformScreenAvailable(type: MissionType) = type in MissionType.entries

sealed interface PerformEntryDecision {
    data class Allow(val type: MissionType) : PerformEntryDecision
    data object Missing : PerformEntryDecision
    data object AlreadySubmitted : PerformEntryDecision
    data object Unsupported : PerformEntryDecision
}

fun performEntryDecision(mission: TodayMission?, requestedType: MissionType?): PerformEntryDecision {
    if (mission == null) return PerformEntryDecision.Missing
    if (mission.didSubmit) return PerformEntryDecision.AlreadySubmitted
    val type = mission.type ?: return PerformEntryDecision.Unsupported
    if (requestedType != null && requestedType != type) return PerformEntryDecision.Unsupported
    if (!supportsTemplate(mission.template)) return PerformEntryDecision.Unsupported
    return if (isPerformScreenAvailable(type)) PerformEntryDecision.Allow(type)
        else PerformEntryDecision.Unsupported
}

@Composable
fun MissionTitle(type: MissionType, topic: String) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text("오늘의 ${type.displayName}", style = HType.plex(13, FontWeight.SemiBold), color = HColors.textSecondary)
        Text(topic, style = HType.plex(20, FontWeight.Bold), color = HColors.ink,
            modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun MissionHeader(hasDraft: Boolean, onClose: () -> Unit, trailing: @Composable () -> Unit = {}) {
    var confirming by remember { mutableStateOf(false) }
    val requestClose = { if (hasDraft) confirming = true else onClose() }
    BackHandler(enabled = hasDraft && !confirming) { requestClose() }
    HNavBar(leading = HNavLeading.CLOSE, onLeading = requestClose, trailing = trailing)
    if (confirming) HAlert("그만둘까요?", "지금까지 한 내용은 저장되지 않아요.",
        confirmTitle = "그만두기", cancelTitle = "계속하기",
        onCancel = { confirming = false }, onConfirm = { confirming = false; onClose() })
}

/** 새 틀 수행 화면(한 단어·후기·밸런스·슬라이더)의 공통 뼈대: 닫기 헤더, 제목·안내, 입력, 올리기 버튼. */
@Composable
internal fun TemplateMissionScaffold(type: MissionType, topic: String, guide: String?, hasDraft: Boolean,
    canSubmit: Boolean, onClose: () -> Unit, onSubmit: suspend () -> Boolean, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 20.dp)) { MissionHeader(hasDraft, onClose) }
        Box(Modifier.weight(1f)) {
            HCtaScaffold(horizontalPadding = 20.dp, bottomInset = 32.dp, content = {
                Spacer(Modifier.height(16.dp))
                MissionTitle(type, topic)
                if (guide != null) Text(guide, Modifier.padding(top = 6.dp), style = HType.caption13,
                    color = HColors.textSecondary)
                Spacer(Modifier.height(24.dp))
                content()
            }, cta = { SubmitButton(canSubmit, onSubmit) })
        }
    }
}

/** 올리는 동안 다시 누를 수 없고, 실패하면(false·예외) 다시 누를 수 있어요. 입력은 화면에 그대로 남아요. */
@Composable
internal fun SubmitButton(enabled: Boolean, onSubmit: suspend () -> Boolean) {
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    HButton(if (submitting) "올리는 중..." else "올리기", enabled = enabled && !submitting) {
        if (!submitting) { submitting = true; scope.launch {
            try { if (!onSubmit()) submitting = false }
            catch (error: Exception) { submitting = false; if (error is CancellationException) throw error }
        } }
    }
}

/** 새 틀의 글 입력 칸. 한도·필터는 [onValueChange] 쪽에서 [applying] 으로 적용해요(`TemplateInputRules`). */
@Composable
internal fun MissionTextBox(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, placeholder: String,
    counter: String,
    modifier: Modifier = Modifier, label: String? = null, singleLine: Boolean = false, minHeight: Dp = 54.dp,
    textStyle: TextStyle = HType.plex(16, lineHeightMultiple = 1.6f)) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (label != null) Text(label, style = HType.fieldLabel, color = HColors.textSecondary)
        val shape = RoundedCornerShape(HRadius.input)
        BasicTextField(value, onValueChange, Modifier.fillMaxWidth().heightIn(min = minHeight)
            .border(1.dp, HColors.border, shape).padding(horizontal = 16.dp, vertical = 14.dp),
            textStyle = textStyle.copy(color = HColors.ink), singleLine = singleLine,
            cursorBrush = SolidColor(HColors.ink), decorationBox = { inner ->
                Box {
                    if (value.text.isEmpty()) Text(placeholder, style = textStyle, color = HColors.textTertiary)
                    inner()
                }
            })
        Text(counter, Modifier.align(Alignment.End), style = HType.caption13.copy(fontFeatureSettings = "tnum"),
            color = HColors.textSecondary)
    }
}
