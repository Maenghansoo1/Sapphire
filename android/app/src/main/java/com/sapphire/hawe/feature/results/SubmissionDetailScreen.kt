package com.sapphire.hawe.feature.results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.model.MissionSpec
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.Submission
import com.sapphire.hawe.core.net.SubmissionRepository
import com.sapphire.hawe.designsystem.components.HAvatar
import com.sapphire.hawe.designsystem.components.HNavBar
import com.sapphire.hawe.designsystem.components.HNavLeading
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HType

@Composable
fun SubmissionDetailScreen(groupID: Long, missionID: Long, submissionID: Long, store: DataStore,
    repository: SubmissionRepository, onBack: () -> Unit) {
    val result = store.result(groupID, missionID)
    val submission = result?.submissions?.find { it.id == submissionID }
    if (result == null || submission == null) {
        Box(Modifier.fillMaxSize().background(HColors.bg))
        return
    }
    Column(Modifier.fillMaxSize().background(HColors.bg).imePadding()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Box {
                HNavBar(leading = HNavLeading.BACK, onLeading = onBack)
                Row(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 52.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    HAvatar(submission.author.avatar, size = 30.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(submission.author.nickname, style = HType.plex(15, FontWeight.SemiBold),
                            color = HColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${koreanMonthDayText(submission.createdAt)} · ${result.type.displayName}${if (submission.isLate) " · $LATE_LABEL" else ""}",
                            style = HType.caption13, color = HColors.textSecondary, maxLines = 1)
                    }
                }
            }
            Text(result.topic, Modifier.fillMaxWidth(), style = HType.caption13,
                color = HColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            DetailArtwork(submission, result.type, result.spec)
            Spacer(Modifier.height(24.dp))
            SubmissionSocial(submission.id, submission.reactions,
                store.group(groupID)?.members.orEmpty(), repository)
        }
    }
}

@Composable
private fun DetailArtwork(submission: Submission, type: MissionType, spec: MissionSpec) {
    when {
        type == MissionType.PHOTO -> PhotoArtwork(submission, 300.dp)
        type == MissionType.DRAW -> DrawingArtwork(submission, 300.dp, 240.dp)
        hasTemplateAnswer(submission.template) -> SelectionContainer {
            Box(Modifier.fillMaxWidth().background(HColors.surface, RoundedCornerShape(20.dp)).padding(24.dp)) {
                TemplateAnswer(submission, spec, large = true)
            }
        }
        else -> SelectionContainer {
            Text(submission.text ?: "", Modifier.fillMaxWidth()
                .background(HColors.surface, RoundedCornerShape(20.dp)).padding(24.dp),
                style = HType.plex(19, FontWeight.Medium, 1.7f), color = HColors.ink)
        }
    }
}
