package com.sapphire.hawe.feature.mission

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.data.SubmissionContent
import com.sapphire.hawe.core.model.MissionSpec
import com.sapphire.hawe.core.model.MissionTemplates
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.designsystem.tokens.HType

/** 한 단어 일기(TEXT_ONE_WORD): 단어 하나(20자, 띄어쓰기 없음) + 이유(선택, 100자). */
@Composable
fun OneWordMissionScreen(topic: String, onClose: () -> Unit, onSubmit: suspend (SubmissionContent) -> Boolean) {
    var word by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var reason by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val state = OneWordState(word.text, reason.text)
    TemplateMissionScaffold(MissionType.WRITE, topic,
        templateGuideText(MissionTemplates.TEXT_ONE_WORD, MissionSpec()), state.hasDraft, state.canSubmit,
        onClose, { onSubmit(state.content()) }) {
        MissionTextBox(word, { word = it.applying(::oneWordInput) }, "한 단어", state.wordCounter, label = "한 단어",
            singleLine = true, minHeight = 64.dp, textStyle = HType.plex(22, FontWeight.Bold))
        Spacer(Modifier.height(20.dp))
        MissionTextBox(reason, { reason = it.applying { text -> reasonInput(text, TemplateLimits.ONE_WORD_REASON) } },
            "왜 이 단어인지 적어도 좋아요", state.reasonCounter, label = "이유 (선택)", minHeight = 120.dp)
    }
}
