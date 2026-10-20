package com.sapphire.hawe.feature.results

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.net.ApiException
import com.sapphire.hawe.core.net.MissionRepository
import com.sapphire.hawe.core.net.SubmissionRepository
import com.sapphire.hawe.core.model.MissionResult
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.Submission
import com.sapphire.hawe.core.model.isAggregateTemplate
import com.sapphire.hawe.designsystem.components.HAvatar
import com.sapphire.hawe.designsystem.components.HButton
import com.sapphire.hawe.designsystem.components.HNavBar
import com.sapphire.hawe.designsystem.components.HNavLeading
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HType
import java.time.Instant
import kotlinx.coroutines.CancellationException

const val LATE_LABEL = "지각"

@Composable
fun ResultsScreen(groupID: Long, missionID: Long, store: DataStore, repository: SubmissionRepository,
    missions: MissionRepository, session: AppSession, onBack: () -> Unit, onDetail: (Long) -> Unit,
    onLocked: () -> Unit, onHome: () -> Unit) {
    var refresh by remember(missionID) { mutableIntStateOf(0) }
    var failed by remember(missionID) { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(missionID, refresh) {
        failed = false
        try {
            val feed = repository.feed(missionID, session.account.userId)
            if (session.state == AppSession.State.SIGNED_IN) store.applyFeed(groupID, missionID, feed)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is ApiException && error.code == "FEED_LOCKED") onLocked()
            else if (error is ApiException && error.code in setOf("NOT_GROUP_MEMBER", "MISSION_NOT_FOUND")) onHome()
            else failed = true
            return@LaunchedEffect
        }
        // 달력에서 연 지난 미션은 밸런스 선택지·그림 대상을 모르니 미션 정보를 한 번 받아 와요
        // (실패하면 라벨 없이, AI 추측은 판정 없이 보여요).
        val template = store.result(groupID, missionID)?.template ?: return@LaunchedEffect
        if ((isAggregateTemplate(template) || template.startsWith("DRAW_")) && store.needsMissionSpec(groupID, missionID)) {
            try {
                val mission = missions.mission(missionID)
                if (session.state == AppSession.State.SIGNED_IN) store.applyMissionSpec(missionID, mission.template, mission.spec)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            }
        }
    }
    // 집계 틀의 그룹 결과. 제출이 늘면 값이 바뀌어서 피드와 같이 다시 불러요. 오류로 화면을 닫는 건 피드 쪽만 해요.
    val template = store.result(groupID, missionID)?.template
    var aggregate by remember(missionID) { mutableStateOf<AggregateLoad>(AggregateLoad.Loading) }
    var aggregateRetry by remember(missionID) { mutableIntStateOf(0) }
    LaunchedEffect(missionID, refresh, aggregateRetry, template) {
        if (template == null || !isAggregateTemplate(template)) return@LaunchedEffect
        if (aggregate !is AggregateLoad.Loaded) aggregate = AggregateLoad.Loading
        aggregate = try {
            repository.result(missionID)?.let { AggregateLoad.Loaded(it) } ?: AggregateLoad.Hidden
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            when {
                error is ApiException && error.code in setOf("FEED_LOCKED", "RESULT_NOT_SUPPORTED",
                    "NOT_GROUP_MEMBER", "MISSION_NOT_FOUND") -> AggregateLoad.Hidden
                aggregate is AggregateLoad.Loaded -> aggregate
                else -> AggregateLoad.Failed
            }
        }
    }
    DisposableEffect(owner, missionID) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val result = store.result(groupID, missionID)
    if (failed) {
        Column(Modifier.fillMaxSize().background(HColors.bg), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("결과를 불러오지 못했어요", style = HType.sectionTitle, color = HColors.ink)
            Spacer(Modifier.height(16.dp))
            HButton("다시 시도") { refresh++ }
        }
        return
    }
    if (result == null) { Box(Modifier.fillMaxSize().background(HColors.bg)); return }
    Column(Modifier.fillMaxSize().background(HColors.bg).padding(horizontal = 20.dp)) {
        HNavBar("${koreanMonthDayText(result.date)} · ${result.type.displayName}",
            leading = HNavLeading.BACK, onLeading = onBack,
            titleStyle = HType.plex(15, FontWeight.SemiBold))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Column(Modifier.padding(top = 8.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(result.topic, style = HType.plex(22, FontWeight.Bold), color = HColors.ink)
                Text("${progressText(result.submissions.size, result.memberCount)} · ${hintText(result.type)}",
                    style = HType.caption13, color = HColors.textSecondary)
            }
            if (isAggregateTemplate(result.template)) AggregateCard(aggregate, result.spec,
                onRetry = { aggregateRetry++ }, modifier = Modifier.padding(bottom = 20.dp))
            when (result.type) {
                MissionType.PHOTO -> PhotoFeed(result, onDetail)
                MissionType.DRAW -> DrawingGrid(result, onDetail)
                MissionType.WRITE -> AnswerCards(result, onDetail)
            }
            if (result.pending.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                PendingMembers(result.pending)
            }
        }
    }
}

@Composable
private fun PhotoFeed(result: MissionResult, onDetail: (Long) -> Unit) {
    SubmissionGrid(result.submissions, onDetail) { submission ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PhotoArtwork(submission, 165.dp)
            GridAuthor(submission)
        }
    }
}

@Composable
private fun DrawingGrid(result: MissionResult, onDetail: (Long) -> Unit) {
    SubmissionGrid(result.submissions, onDetail) { submission ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DrawingArtwork(submission, 165.dp, 130.dp, cornerRadius = 18.dp)
            GridAuthor(submission)
        }
    }
}

@Composable
private fun AnswerCards(result: MissionResult, onDetail: (Long) -> Unit) {
    SubmissionGrid(result.submissions, onDetail) { submission ->
        Column(Modifier.fillMaxWidth().height(165.dp).clip(RoundedCornerShape(20.dp))
            .background(HColors.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GridAuthor(submission)
            Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                TemplateAnswer(submission, result.spec)
            }
        }
    }
}

@Composable
private fun SubmissionGrid(submissions: List<Submission>, onDetail: (Long) -> Unit,
    cell: @Composable (Submission) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        submissions.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { submission ->
                    Box(Modifier.weight(1f).clickable { onDetail(submission.id) }
                        .semantics { contentDescription = "${submission.author.nickname} 작품 보기" }) {
                        cell(submission)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GridAuthor(submission: Submission) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        HAvatar(submission.author.avatar, size = 26.dp)
        Text(submission.author.nickname, Modifier.weight(1f), style = HType.plex(14, FontWeight.SemiBold),
            color = HColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (submission.isLate) Text(LATE_LABEL, style = HType.caption12, color = HColors.textSecondary)
    }
}
