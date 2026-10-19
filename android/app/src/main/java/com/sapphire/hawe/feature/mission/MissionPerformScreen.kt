package com.sapphire.hawe.feature.mission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sapphire.hawe.core.data.SubmissionContent
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.MissionSpec
import com.sapphire.hawe.core.model.MissionTemplates
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.components.HToastHost
import com.sapphire.hawe.designsystem.components.rememberHToastState
import androidx.compose.runtime.LaunchedEffect

@Composable
fun MissionPerformScreen(type: MissionType, topic: String, template: String, spec: MissionSpec, onClose: () -> Unit,
    message: String? = null, onMessageShown: () -> Unit = {},
    onSubmit: suspend (SubmissionContent) -> Boolean) {
    val toast = rememberHToastState()
    LaunchedEffect(message) { if (message != null) { toast.show(message); onMessageShown() } }
    Box(Modifier.fillMaxSize().background(HColors.bg)) {
        when (type) {
            MissionType.WRITE -> when (template) {
                MissionTemplates.TEXT_ONE_WORD -> OneWordMissionScreen(topic, onClose, onSubmit)
                MissionTemplates.TEXT_REVIEW -> ReviewMissionScreen(topic, onClose, onSubmit)
                MissionTemplates.TEXT_BALANCE -> BalanceMissionScreen(topic, spec, onClose, onSubmit)
                else -> WriteMissionScreen(topic, template, spec, onClose) { onSubmit(SubmissionContent.Text(it)) }
            }
            MissionType.PHOTO -> PhotoMissionScreen(topic, template, onClose) { onSubmit(SubmissionContent.Image(it, "jpg")) }
            MissionType.DRAW -> DrawMissionScreen(topic, template, spec, onClose) { onSubmit(SubmissionContent.Image(it, "png")) }
        }
        HToastHost(toast)
    }
}
