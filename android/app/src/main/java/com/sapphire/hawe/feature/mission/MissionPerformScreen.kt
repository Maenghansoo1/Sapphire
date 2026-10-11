package com.sapphire.hawe.feature.mission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sapphire.hawe.core.data.SubmissionContent
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.designsystem.tokens.HColors

@Composable
fun MissionPerformScreen(type: MissionType, topic: String, onClose: () -> Unit,
    onSubmit: (SubmissionContent) -> Unit) {
    Box(Modifier.fillMaxSize().background(HColors.bg)) {
        when (type) {
            MissionType.WRITE -> WriteMissionScreen(topic, onClose) { onSubmit(SubmissionContent.Text(it)) }
            MissionType.PHOTO -> PhotoMissionScreen(topic, onClose) { onSubmit(SubmissionContent.Image(it, "jpg")) }
            else -> Unit
        }
    }
}
