package com.sapphire.hawe.feature.insight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.dev.AppClock
import com.sapphire.hawe.core.model.GroupInsight
import com.sapphire.hawe.core.net.ApiException
import com.sapphire.hawe.core.net.CalendarRepository
import com.sapphire.hawe.core.net.InsightOutcome
import com.sapphire.hawe.core.net.InsightRepository
import com.sapphire.hawe.core.net.loadInsight
import com.sapphire.hawe.core.net.toUserMessage
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.tokens.*
import com.sapphire.hawe.feature.records.currentMonth
import com.sapphire.hawe.feature.records.monthTitle
import java.time.Instant
import kotlinx.coroutines.CancellationException

@Composable
fun GroupInsightScreen(groupId: Long, store: DataStore, insights: InsightRepository,
    calendar: CalendarRepository, onBack: () -> Unit, onLeft: () -> Unit, onResults: (Long) -> Unit) {
    val current = currentMonth(AppClock.now())
    var month by rememberSaveable { mutableStateOf(current) }
    var retry by remember { mutableIntStateOf(0) }
    var outcome by remember(month) { mutableStateOf<InsightOutcome?>(null) }
    var failure by remember(month) { mutableStateOf(false) }
    var opening by remember(month) { mutableStateOf<Long?>(null) }
    val toast = rememberHToastState()
    LaunchedEffect(groupId, month, retry) {
        outcome = null; failure = false
        try {
            loadInsight({ insights.load(groupId, month) }) { outcome = it }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is ApiException && (e.code == "NOT_GROUP_MEMBER" || e.code == "GROUP_NOT_FOUND")) {
                store.leave(groupId); onLeft()
            } else failure = true
        }
    }
    LaunchedEffect(groupId, month, opening) {
        val missionId = opening ?: return@LaunchedEffect
        try {
            val records = calendar.month(groupId, month)
            store.applyCalendar(groupId, month, records)
            when (store.calendarRecord(groupId, missionId)?.didSubmit) {
                true -> onResults(missionId)
                false -> toast.show("먼저 미션을 올려야 볼 수 있어요")
                null -> toast.show("이 글은 지금 열 수 없어요")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is ApiException && (e.code == "NOT_GROUP_MEMBER" || e.code == "GROUP_NOT_FOUND")) {
                store.leave(groupId); onLeft()
            } else toast.show(e.toUserMessage())
        } finally { opening = null }
    }
    Box(Modifier.fillMaxSize().background(HColors.bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = HLayout.screenPaddingCompact)) {
            HNavBar("우리 그룹 이야기", leading = HNavLeading.BACK, onLeading = onBack)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MonthArrow(HIcon.CHEVRON_LEFT, "이전 달", canShowPreviousInsight(month, current)) {
                        month = month.minusMonths(1)
                    }
                    Text(monthTitle(month), Modifier.weight(1f), style = HType.plex(18, FontWeight.Bold),
                        color = HColors.ink, textAlign = TextAlign.Center)
                    MonthArrow(HIcon.CHEVRON_RIGHT, "다음 달", canShowNextInsight(month, current)) {
                        month = month.plusMonths(1)
                    }
                }
                when {
                    failure -> StatusPanel("불러오지 못했어요") { retry++ }
                    outcome is InsightOutcome.TimedOut -> StatusPanel("아직 준비 중이에요. 잠시 후 다시 열어 주세요") { retry++ }
                    outcome is InsightOutcome.Ready -> {
                        val ready = outcome as InsightOutcome.Ready
                        if (ready.updating) Text("업데이트 중…", style = HType.caption13, color = HColors.textSecondary)
                        if (ready.insight.empty) Text("이 달에는 아직 쌓인 기록이 없어요", style = HType.body15,
                            color = HColors.textSecondary)
                        else InsightContent(ready.insight, opening != null) { opening = it }
                    }
                    else -> Text("우리 이야기를 정리하고 있어요…", style = HType.body15,
                        color = HColors.textSecondary)
                }
            }
        }
        HToastHost(toast)
    }
}

@Composable private fun MonthArrow(icon: HIcon, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clickable(enabled = enabled, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        HIconView(icon, size = 20.dp, color = if (enabled) HColors.ink else HColors.disabled)
    }
}

@Composable private fun StatusPanel(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(message, style = HType.body15, color = HColors.textSecondary, textAlign = TextAlign.Center)
        HButton("다시 시도", onClick = onRetry)
    }
}

// 임시 UI: Figma 디자인이 정해지면 이 섹션의 표현만 교체해요.
@Composable private fun InsightContent(insight: GroupInsight, opening: Boolean, onOpen: (Long) -> Unit) {
    insight.activityIndex?.let { activity ->
        InsightCard("활동 지수") {
            Box(Modifier.size(128.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val width = 9.dp.toPx()
                    drawArc(HColors.line, 0f, 360f, false, style = Stroke(width, cap = StrokeCap.Round))
                    drawArc(HColors.accent, -90f, activity.score.coerceIn(0, 100) * 3.6f, false,
                        style = Stroke(width, cap = StrokeCap.Round))
                }
                Text("${activity.score}", style = HType.plex(34, FontWeight.Bold), color = HColors.ink)
            }
            insightDelta(activity.delta)?.let {
                Text("전월 대비 $it", Modifier.align(Alignment.CenterHorizontally),
                    style = HType.caption13, color = HColors.textSecondary)
            }
            RateBar("참여", insightPercent(activity.participationRate))
            RateBar("정시", insightPercent(activity.onTimeRate))
            RateBar("교류", insightPercent(activity.interactionRate))
        }
    }
    insight.fastest?.let { fastest ->
        InsightCard("가장 빨리 낸 사람") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HAvatar(fastest.profile.avatar, size = 48.dp)
                Column {
                    Text(fastest.profile.nickname, style = HType.plex(17, FontWeight.Bold), color = HColors.ink)
                    Text("${fastest.firstCount}번 가장 먼저 냈어요", style = HType.body15, color = HColors.textSecondary)
                }
            }
            Text(insightResponseTime(fastest.averageResponseSeconds), style = HType.body15, color = HColors.textSecondary)
        }
    }
    if (insight.topWords.isNotEmpty()) InsightCard("많이 쓰인 단어") {
        val maximum = insight.topWords.maxOf { it.count }.coerceAtLeast(1)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            insight.topWords.forEach { word ->
                Row(Modifier.background(HColors.accentSubtle, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(word.word, style = HType.plex(insightWordSize(word.count, maximum), FontWeight.SemiBold),
                        color = HColors.ink)
                    Spacer(Modifier.width(4.dp))
                    Text("${word.count}번", style = HType.caption12, color = HColors.textSecondary)
                }
            }
        }
    }
    insight.mostReacted?.let { reacted ->
        InsightCard("가장 반응이 많았던 글") {
            Column(Modifier.fillMaxWidth().clickable(enabled = !opening) { onOpen(reacted.missionId) },
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(reacted.missionContent, style = HType.plex(16, FontWeight.SemiBold), color = HColors.ink)
                Text("${reacted.authorNickname} · 반응 ${reacted.reactionCount} · 댓글 ${reacted.commentCount} ${reacted.topSticker?.emoji.orEmpty()}",
                    style = HType.caption13, color = HColors.textSecondary)
            }
        }
    }
    if (insight.highlights.isNotEmpty()) InsightCard("하이라이트") {
        insight.highlights.forEach { Text("• $it", style = HType.body15, color = HColors.ink) }
    }
}

@Composable private fun InsightCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(HColors.surface, RoundedCornerShape(16.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HIconView(HIcon.INSIGHT_TEMP, size = 20.dp, color = HColors.accent)
            Text(title, style = HType.sectionTitle, color = HColors.ink)
        }
        content()
    }
}

@Composable private fun RateBar(label: String, percent: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = HType.caption13, color = HColors.textSecondary)
        Box(Modifier.weight(1f).height(6.dp).background(HColors.line, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(percent / 100f).fillMaxHeight()
                .background(HColors.accent, RoundedCornerShape(3.dp)))
        }
        Text("$percent%", style = HType.caption13, color = HColors.textSecondary)
    }
}
