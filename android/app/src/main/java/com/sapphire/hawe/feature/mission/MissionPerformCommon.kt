package com.sapphire.hawe.feature.mission

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.TodayMission
import com.sapphire.hawe.designsystem.components.HAlert
import com.sapphire.hawe.designsystem.components.HNavBar
import com.sapphire.hawe.designsystem.components.HNavLeading
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HType

fun isPerformScreenAvailable(type: MissionType) = type in MissionType.entries

sealed interface PerformEntryDecision {
    data class Allow(val type: MissionType) : PerformEntryDecision
    data object Missing : PerformEntryDecision
    data object AlreadySubmitted : PerformEntryDecision
    data object Unsupported : PerformEntryDecision
}

fun performEntryDecision(mission: TodayMission?, requestedType: MissionType?): PerformEntryDecision = when {
    mission == null -> PerformEntryDecision.Missing
    mission.didSubmit -> PerformEntryDecision.AlreadySubmitted
    !isPerformScreenAvailable(requestedType ?: mission.type) -> PerformEntryDecision.Unsupported
    else -> PerformEntryDecision.Allow(requestedType ?: mission.type)
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
