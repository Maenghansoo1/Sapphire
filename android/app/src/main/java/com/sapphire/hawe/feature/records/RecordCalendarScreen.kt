package com.sapphire.hawe.feature.records

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.model.DayRecord
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.components.HNavBar
import com.sapphire.hawe.designsystem.components.HNavLeading
import com.sapphire.hawe.designsystem.layout.ctaBottomPadding
import com.sapphire.hawe.designsystem.tokens.*
import com.sapphire.hawe.feature.results.koreanMonthDayText
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Composable
fun RecordCalendarScreen(groupID: Long, store: DataStore, onBack: () -> Unit,
    onResults: (Long) -> Unit) {
    val group = store.group(groupID)
    if (group == null) { Box(Modifier.fillMaxSize().background(HColors.bg)); return }
    val now = Instant.now()
    val today = seoulDate(now)
    val first = firstMonth(group.createdAt)
    val last = currentMonth(now)
    val records = recordsByDate(store.records(groupID))
    var month by rememberSaveable { mutableStateOf(last) }
    var selected by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(Unit) { if (selected == null) selected = initialSelectedDate(records) }
    val bottom = ctaBottomPadding(32.dp)
    Column(Modifier.fillMaxSize().background(HColors.bg).padding(horizontal = HLayout.screenPaddingCompact)) {
        HNavBar("우리의 기록", leading = HNavLeading.BACK, onLeading = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottom)) {
            MonthHeader(month, canGoPrevious(month, first), canGoNext(month, last),
                onPrevious = { month = month.minusMonths(1) }, onNext = { month = month.plusMonths(1) })
            Spacer(Modifier.height(12.dp))
            CalendarGrid(month, today, records, selected, onSelect = { selected = it })
            selected?.let { records[it] }?.let { record ->
                Spacer(Modifier.height(20.dp))
                SelectedCard(record) { record.missionID?.let(onResults) }
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, canPrevious: Boolean, canNext: Boolean,
    onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        MonthButton(HIcon.CHEVRON_LEFT, "이전 달", canPrevious, onPrevious)
        Text(monthTitle(month), Modifier.weight(1f), style = HType.plex(18, FontWeight.Bold),
            color = HColors.ink, textAlign = TextAlign.Center)
        MonthButton(HIcon.CHEVRON_RIGHT, "다음 달", canNext, onNext)
    }
}

@Composable
private fun MonthButton(icon: HIcon, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(HLayout.minTouch).clickable(enabled = enabled, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        HIconView(icon, size = 20.dp, color = if (enabled) HColors.ink else HColors.controlBorder)
    }
}

@Composable
private fun CalendarGrid(month: YearMonth, today: LocalDate, records: Map<LocalDate, DayRecord>,
    selected: LocalDate?, onSelect: (LocalDate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            WEEKDAY_LABELS.forEach { label ->
                Box(Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.Center) {
                    Text(label, style = HType.plex(12, FontWeight.SemiBold), color = HColors.textSecondary)
                }
            }
        }
        monthWeeks(month).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    if (date == null) Spacer(Modifier.weight(1f).height(52.dp))
                    else DayCell(date, today, records[date], date == selected && records[date] != null,
                        Modifier.weight(1f), onSelect)
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, today: LocalDate, record: DayRecord?, isSelected: Boolean,
    modifier: Modifier, onSelect: (LocalDate) -> Unit) {
    val isToday = date == today
    val isFuture = date.isAfter(today)
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.height(52.dp)
        .background(if (isSelected) HColors.ink else Color.Transparent, shape)
        .then(if (isToday && !isSelected) Modifier.border(1.5.dp, HColors.ink, shape) else Modifier)
        .clickable(enabled = record != null) { onSelect(date) }
        .semantics { contentDescription = cellDescription(date, record, isToday) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
        Text(date.dayOfMonth.toString(),
            style = HType.plex(15, if (record != null) FontWeight.Bold else FontWeight.Medium),
            color = when { isSelected -> HColors.onAccent; isFuture -> HColors.textDisabled; else -> HColors.ink })
        Text(record?.type?.displayName ?: " ", style = HType.plex(10, FontWeight.SemiBold),
            color = if (isSelected) HColors.onAccent else HColors.textSecondary)
    }
}

@Composable
private fun SelectedCard(record: DayRecord, onResults: () -> Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp, HColors.line, RoundedCornerShape(HRadius.card)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("${koreanMonthDayText(record.date)} · ${record.type.displayName}",
            style = HType.caption13, color = HColors.textSecondary)
        Text(record.topic, style = HType.plex(17, FontWeight.Bold), color = HColors.ink)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${record.doneCount} / ${record.memberCount}명 참여", style = HType.caption13,
                color = HColors.textSecondary)
            Spacer(Modifier.weight(1f))
            if (record.didSubmit) Box(Modifier.height(44.dp).clickable(onClick = onResults),
                contentAlignment = Alignment.Center) {
                Box(Modifier.height(36.dp).background(HColors.surface, CircleShape).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center) {
                    Text("모아보기", style = HType.plex(13, FontWeight.SemiBold), color = HColors.ink)
                }
            } else Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
                Text(NOT_SUBMITTED_NOTICE, style = HType.caption13, color = HColors.textTertiary)
            }
        }
    }
}
