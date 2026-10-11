package com.sapphire.hawe.feature.mission

import androidx.compose.foundation.border
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
import java.text.BreakIterator

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

data class WriteMissionState(val text: String = "") {
    fun withText(input: String) = copy(text = limitGraphemes(input))
    val count: Int get() = graphemeCount(text)
    val trimmed: String get() = text.trim()
    val canSubmit: Boolean get() = trimmed.isNotEmpty()
    val counterText: String get() = "$count / $MAX_LENGTH"
}

@Composable
fun WriteMissionScreen(topic: String, onClose: () -> Unit, onSubmit: (String) -> Unit) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val state = WriteMissionState(value.text)
    var submitted by remember { mutableStateOf(false) }
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
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(360.dp)
                    .border(1.dp, HColors.line, RoundedCornerShape(HRadius.card))
                    .clickable(interactionSource = interaction, indication = null) {
                        focusRequester.requestFocus()
                        keyboard?.show()
                    }.padding(20.dp)) {
                    BasicTextField(value = value, onValueChange = { next ->
                        val limited = limitGraphemes(next.text)
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
                HButton("올리기", enabled = state.canSubmit && !submitted) {
                    if (!submitted) { submitted = true; onSubmit(state.trimmed) }
                }
            })
        }
    }
}
