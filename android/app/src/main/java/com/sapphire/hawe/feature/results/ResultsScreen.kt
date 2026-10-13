package com.sapphire.hawe.feature.results

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.sapphire.hawe.feature.draw.AiGuessChip
import com.sapphire.hawe.feature.draw.AiGuessDialog
import com.sapphire.hawe.feature.draw.AiGuessRunner
import com.sapphire.hawe.feature.draw.DrawSubjectMatcher
import com.sapphire.hawe.feature.draw.loadInkImage
import com.sapphire.hawe.feature.draw.rememberAiGuessAvailable
import com.sapphire.hawe.feature.draw.rememberAiGuessRunner
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

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
        // 달력에서 연 지난 미션은 밸런스 선택지·슬라이더 라벨·그림 대상을 모르니 미션 정보를 한 번 받아 와요
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
    // AI 심사(B10)는 제출 뒤에 따로 끝나요. 방금 올린 내 사진이 아직이면 5초 뒤 한 번만 더 불러와요(계속 묻지 않음).
    LaunchedEffect(missionID) {
        delay(5_000)
        val submissions = store.result(groupID, missionID)?.submissions.orEmpty()
        if (awaitsAiReview(submissions, Instant.now())) refresh++
    }
    DisposableEffect(owner, missionID) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val result = store.result(groupID, missionID)
    val aiAvailable = rememberAiGuessAvailable()
    val aiGuess = rememberAiGuessRunner()
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
                store.group(groupID)?.members.orEmpty(), session.account.userId,
                onRetry = { aggregateRetry++ }, modifier = Modifier.padding(bottom = 20.dp))
            when (result.type) {
                MissionType.PHOTO -> PhotoFeed(result, onDetail)
                MissionType.DRAW -> DrawingGrid(result, onDetail, aiAvailable, aiGuess)
                MissionType.WRITE, MissionType.SLIDER -> AnswerCards(result, onDetail)
            }
            if (result.pending.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                PendingMembers(result.pending)
            }
        }
    }
    AiGuessDialog(aiGuess)
}

@Composable
private fun PhotoFeed(result: MissionResult, onDetail: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        result.submissions.forEach { submission ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HAvatar(submission.author.avatar, size = 34.dp)
                    Text(submission.author.nickname, Modifier.weight(1f), style = HType.plex(15, FontWeight.SemiBold),
                        color = HColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(koreanTimeText(submission.createdAt), style = HType.caption13, color = HColors.textSecondary)
                    if (submission.isLate) Text(LATE_LABEL, style = HType.caption12, color = HColors.textSecondary)
                }
                PhotoArtwork(submission, 260.dp, Modifier.clickable { onDetail(submission.id) })
                submission.aiReview?.let { AiReviewLine(it) }
            }
        }
    }
}

@Composable
private fun DrawingGrid(result: MissionResult, onDetail: (Long) -> Unit, aiAvailable: Boolean, aiGuess: AiGuessRunner) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        result.submissions.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { submission ->
                    Column(Modifier.weight(1f).clickable { onDetail(submission.id) },
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box {
                            DrawingArtwork(submission, 165.dp, 130.dp, cornerRadius = 18.dp)
                            if (aiAvailable) AiGuessChip(Modifier.align(Alignment.BottomEnd)) {
                                aiGuess.guess(mine = submission.isMine, load = { loadInkImage(context, imageModel(submission)) },
                                    subjectOf = { DrawSubjectMatcher.match(result.template, result.topic, result.spec, it) })
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            HAvatar(submission.author.avatar, size = 26.dp)
                            Text(submission.author.nickname, Modifier.weight(1f),
                                style = HType.plex(14, FontWeight.SemiBold), color = HColors.ink,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (submission.isLate) Text(LATE_LABEL, style = HType.caption12, color = HColors.textSecondary)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AnswerCards(result: MissionResult, onDetail: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        result.submissions.forEach { submission ->
            Column(Modifier.fillMaxWidth().background(HColors.surface, RoundedCornerShape(20.dp))
                .clickable { onDetail(submission.id) }.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    HAvatar(submission.author.avatar, Modifier.background(HColors.bg, CircleShape), size = 30.dp)
                    Text(submission.author.nickname, Modifier.weight(1f), style = HType.plex(14, FontWeight.SemiBold),
                        color = HColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(koreanTimeText(submission.createdAt), style = HType.caption12, color = HColors.textSecondary)
                    if (submission.isLate) Text(LATE_LABEL, style = HType.caption12, color = HColors.textSecondary)
                }
                TemplateAnswer(submission, result.spec)
            }
        }
    }
}
