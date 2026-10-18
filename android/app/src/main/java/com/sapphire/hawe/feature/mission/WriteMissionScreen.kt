package com.sapphire.hawe.feature.mission

import androidx.compose.foundation.border
<<<<<<< HEAD
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.components.HButton
import com.sapphire.hawe.designsystem.layout.HCtaScaffold
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HRadius
import com.sapphire.hawe.designsystem.tokens.HType
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.MissionSpec
import java.text.BreakIterator
import kotlinx.coroutines.launch

private const val MAX_LENGTH = 300

fun graphemeCount(text: String): Int {
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    var count = 0
    while (iterator.next() != BreakIterator.DONE) count++
    return count
}

fun limitGraphemes(text: String, limit: Int = MAX_LENGTH): String {
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    var end = 0
    repeat(limit) { end = iterator.next().takeIf { it != BreakIterator.DONE } ?: return text }
    return text.substring(0, end)
}

data class WriteMissionSpec(val maxLength: Int = MAX_LENGTH, val acrosticWord: String? = null)

data class WriteMissionState(val text: String = "", val spec: WriteMissionSpec = WriteMissionSpec()) {
    fun withText(input: String) = copy(text = limitGraphemes(input, spec.maxLength))
    val count: Int get() = graphemeCount(text)
    val trimmed: String get() = text.trim()
    val canSubmit: Boolean get() = trimmed.isNotEmpty() && count <= spec.maxLength && acrosticError() == null
    val counterText: String get() = "$count / ${spec.maxLength}"

    fun acrosticError(): String? {
        val word = spec.acrosticWord ?: return null
        val lines = text.lines().dropLastWhile { it.isBlank() }
        if (lines.size != 3) return "3줄로 써 주세요"
        for (index in 0..2) {
            val line = lines[index].trim()
            if (line.isEmpty() || limitGraphemes(line, 1) != word.getOrNull(index)?.toString())
                return "‘${word.getOrNull(index) ?: ""}’로 시작해 주세요"
        }
        return null
    }
}

@Composable
fun WriteMissionScreen(topic: String, template: String, missionSpec: MissionSpec,
    onClose: () -> Unit, onSubmit: suspend (String) -> Boolean) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val spec = WriteMissionSpec(if (template == "TEXT_FILL_BLANK") 100 else MAX_LENGTH,
        missionSpec.word.takeIf { template == "TEXT_ACROSTIC" })
    val state = WriteMissionState(value.text, spec)
    var submitted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val interaction = remember { MutableInteractionSource() }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 20.dp)) {
            MissionHeader(state.canSubmit, onClose) {
                Text(state.counterText, style = HType.plex(13).copy(fontFeatureSettings = "tnum"),
                    color = HColors.textSecondary)
            }
        }
        Box(Modifier.weight(1f)) {
            HCtaScaffold(horizontalPadding = 20.dp, bottomInset = 32.dp, content = {
                Spacer(Modifier.height(16.dp))
                MissionTitle(MissionType.WRITE, topic)
                if (spec.acrosticWord != null) Text("각 줄을 ${spec.acrosticWord.map { "‘$it’" }.joinToString(" ")}로 시작해요",
                    style = HType.caption13, color = HColors.textSecondary)
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(360.dp)
                    .border(1.dp, HColors.line, RoundedCornerShape(HRadius.card))
                    .clickable(interactionSource = interaction, indication = null) {
                        focusRequester.requestFocus()
                        keyboard?.show()
                    }.padding(20.dp)) {
                    BasicTextField(value = value, onValueChange = { next ->
                        val limited = limitGraphemes(next.text, spec.maxLength)
                        value = if (limited == next.text) next else TextFieldValue(limited, TextRange(limited.length))
                    }, modifier = Modifier.fillMaxSize().focusRequester(focusRequester),
                        textStyle = HType.plex(17, lineHeightMultiple = 1.7f).copy(color = HColors.ink),
                        cursorBrush = SolidColor(HColors.ink), decorationBox = { inner ->
                            Box {
                                if (value.text.isEmpty()) Text("여기에 적어보세요", style = HType.plex(17), color = HColors.textTertiary)
                                inner()
                            }
                        })
                }
            }, cta = {
                HButton(if (submitted) "올리는 중..." else "올리기", enabled = state.canSubmit && !submitted) {
                    if (!submitted) { submitted = true; scope.launch {
                        try { if (!onSubmit(state.trimmed)) submitted = false }
                        catch (error: Exception) { submitted = false }
                    } }
                }
            })
        }
    }
}
=======
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
>>>>>>> feat/ios-step1
