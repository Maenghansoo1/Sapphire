package com.sapphire.hawe.feature.records

<<<<<<< HEAD
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
=======
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
>>>>>>> feat/ios-step1
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
<<<<<<< HEAD
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.R
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.model.DayRecord
import com.sapphire.hawe.core.model.Stamp
import com.sapphire.hawe.core.dev.AppClock
import com.sapphire.hawe.core.net.CalendarRepository
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.components.HButton
import com.sapphire.hawe.designsystem.components.HNavBar
import com.sapphire.hawe.designsystem.components.HNavLeading
import com.sapphire.hawe.designsystem.layout.ctaBottomPadding
import com.sapphire.hawe.designsystem.tokens.*
import com.sapphire.hawe.feature.results.koreanMonthDayText
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CancellationException

@Composable
fun RecordCalendarScreen(groupID: Long, store: DataStore, calendar: CalendarRepository, session: AppSession,
    onBack: () -> Unit, onResults: (Long) -> Unit) {
    val group = store.group(groupID)
    if (group == null) { Box(Modifier.fillMaxSize().background(HColors.bg)); return }
    val now = AppClock.now()
    val today = seoulDate(now)
    val first = firstMonth(group.createdAt)
    val last = currentMonth(now)
    var month by rememberSaveable { mutableStateOf(last) }
    var selected by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var failed by remember(month) { mutableStateOf(false) }
    LaunchedEffect(groupID, month, refresh) {
        failed = false
        try {
            val loaded = calendar.month(groupID, month)
            if (session.state == AppSession.State.SIGNED_IN) store.applyCalendar(groupID, month, loaded)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (store.records(groupID, month) == null) failed = true
        }
    }
    val loaded = store.records(groupID, month)
    val records = remember(loaded) { recordsByDate(loaded.orEmpty()) }
    LaunchedEffect(month, records) { selected = selectedDateFor(month, selected, records) }
    val bottom = ctaBottomPadding(32.dp)
    Column(Modifier.fillMaxSize().background(HColors.bg).padding(horizontal = HLayout.screenPaddingCompact)) {
        HNavBar("우리의 기록", leading = HNavLeading.BACK, onLeading = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottom)) {
            MonthHeader(month, canGoPrevious(month, first), canGoNext(month, last),
                onPrevious = { month = month.minusMonths(1) }, onNext = { month = month.plusMonths(1) })
            Spacer(Modifier.height(12.dp))
            if (failed) LoadFailed { refresh++ }
            else {
                CalendarGrid(month, today, records, selected, onSelect = { selected = it })
                selected?.let { records[it] }?.let { record ->
                    Spacer(Modifier.height(20.dp))
                    SelectedCard(record) { record.missionID?.let(onResults) }
                }
=======
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.app.LocalStore
import com.sapphire.hawe.designsystem.HColors
import com.sapphire.hawe.designsystem.HIcon
import com.sapphire.hawe.designsystem.HIconView
import com.sapphire.hawe.designsystem.HLayout
import com.sapphire.hawe.designsystem.HNavBar
import com.sapphire.hawe.designsystem.HRadius
import com.sapphire.hawe.designsystem.HScreen
import com.sapphire.hawe.designsystem.HType
import com.sapphire.hawe.designsystem.NavLeading
import com.sapphire.hawe.designsystem.plainClickable
import com.sapphire.hawe.designsystem.plex
import com.sapphire.hawe.model.DayRecord
import com.sapphire.hawe.model.SEOUL
import java.time.LocalDate
import java.time.YearMonth

/**
 * 15 기록 캘린더 (Figma 1:1311)
 * 그룹별 월간 캘린더. 기록 있는 날에 유형 이름(그림/사진/글/음성), 오늘 테두리,
 * 날짜를 고르면 아래 카드 → '모아보기'로 그날 결과 공개 화면 (내가 올린 날만)
 */
@Composable
fun RecordCalendarScreen(groupId: String, onBack: () -> Unit, onOpenResults: (missionId: String) -> Unit) {
    val store = LocalStore.current
    val today = LocalDate.now(SEOUL)
    val records = store.records(groupId).associateBy { it.date }
    val firstMonth = YearMonth.from(store.group(groupId)?.createdAt ?: today)
    val lastMonth = YearMonth.from(today)

    var month by rememberSaveable { mutableStateOf(lastMonth) }
    // 처음에는 가장 최근 기록이 있는 날을 골라 둬요.
    var selected by rememberSaveable { mutableStateOf(records.keys.maxOrNull()) }

    HScreen {
        HNavBar("우리의 기록", modifier = Modifier.padding(horizontal = HLayout.screenPaddingCompact), leading = NavLeading.Back(onBack))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = HLayout.screenPaddingCompact)
                .padding(bottom = 32.dp),
        ) {
            MonthHeader(
                month,
                canGoBack = month > firstMonth,
                canGoForward = month < lastMonth,
                onMove = { month = month.plusMonths(it.toLong()) },
                modifier = Modifier.padding(top = 12.dp),
            )
            Grid(month, today, records, selected, Modifier.padding(top = 12.dp)) { selected = it }
            selected?.let { records[it] }?.let { record ->
                SelectedCard(record, Modifier.padding(top = 20.dp)) { onOpenResults(record.missionId) }
>>>>>>> feat/ios-step1
            }
        }
    }
}

<<<<<<< HEAD
@DrawableRes
private fun stampDrawable(stamp: Stamp): Int = when (stamp) {
    Stamp.GOOD -> R.drawable.stamp_good
    Stamp.LATE -> R.drawable.stamp_late
    Stamp.PENALTY -> R.drawable.stamp_penalty
}

@Composable
private fun StampImage(stamp: Stamp, size: Dp) {
    Image(painterResource(stampDrawable(stamp)), contentDescription = null, modifier = Modifier.size(size))
}

@Composable
private fun LoadFailed(onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("기록을 불러오지 못했어요", style = HType.sectionTitle, color = HColors.ink)
        HButton("다시 시도", onClick = onRetry)
=======
/** 화살표 20 (44 칸) + "2026년 10월" 18 Bold */
@Composable
private fun MonthHeader(month: YearMonth, canGoBack: Boolean, canGoForward: Boolean, onMove: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MonthButton(HIcon.ChevronLeft, canGoBack) { onMove(-1) }
        Spacer(Modifier.weight(1f))
        Text("${month.year}년 ${month.monthValue}월", style = plex(18, FontWeight.Bold), color = HColors.ink)
        Spacer(Modifier.weight(1f))
        MonthButton(HIcon.ChevronRight, canGoForward) { onMove(1) }
>>>>>>> feat/ios-step1
    }
}

@Composable
<<<<<<< HEAD
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
                    if (date == null) Spacer(Modifier.weight(1f).height(56.dp))
                    else DayCell(date, today, records[date], date == selected && records[date] != null,
                        Modifier.weight(1f), onSelect)
=======
private fun MonthButton(icon: HIcon, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(HLayout.minTouch).plainClickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        HIconView(icon, 20.dp, if (enabled) HColors.ink else HColors.controlBorder)
    }
}

/** 요일 줄 28 + 주마다 칸 52 (모서리 14), 간격 4 */
@Composable
private fun Grid(
    month: YearMonth,
    today: LocalDate,
    records: Map<LocalDate, DayRecord>,
    selected: LocalDate?,
    modifier: Modifier = Modifier,
    onSelect: (LocalDate) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            listOf("일", "월", "화", "수", "목", "금", "토").forEach { weekday ->
                Box(Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.Center) {
                    Text(weekday, style = plex(12, FontWeight.SemiBold), color = HColors.textSecondary)
                }
            }
        }
        weeks(month).forEach { week ->
            Row {
                week.forEach { day ->
                    Box(Modifier.weight(1f)) {
                        if (day != null) DayCell(day, today, records[day], isSelected = day == selected && records[day] != null) { onSelect(day) }
                        else Spacer(Modifier.height(52.dp))
                    }
>>>>>>> feat/ios-step1
                }
            }
        }
    }
}

<<<<<<< HEAD
@Composable
private fun DayCell(date: LocalDate, today: LocalDate, record: DayRecord?, isSelected: Boolean,
    modifier: Modifier, onSelect: (LocalDate) -> Unit) {
    val isToday = date == today
    val isFuture = date.isAfter(today)
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.height(56.dp)
        .background(if (isSelected) HColors.ink else Color.Transparent, shape)
        .then(if (isToday && !isSelected) Modifier.border(1.5.dp, HColors.ink, shape) else Modifier)
        .clickable(enabled = record != null) { onSelect(date) }
        .semantics { contentDescription = cellDescription(date, record, isToday) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
        Text(date.dayOfMonth.toString(),
            style = HType.plex(15, if (record != null) FontWeight.Bold else FontWeight.Medium),
            color = when { isSelected -> HColors.onAccent; isFuture -> HColors.textDisabled; else -> HColors.ink })
        val stamp = record?.stamp
        if (stamp != null) StampImage(stamp, 20.dp)
        else Text(record?.type?.displayName ?: " ", style = HType.plex(10, FontWeight.SemiBold),
            color = if (isSelected) HColors.onAccent else HColors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun SelectedCard(record: DayRecord, onResults: () -> Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp, HColors.line, RoundedCornerShape(HRadius.card)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val caption = listOfNotNull(koreanMonthDayText(record.date), record.type.displayName,
            record.stamp?.displayName).joinToString(" · ")
        Text(caption, style = HType.caption13, color = HColors.textSecondary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(record.topic, Modifier.weight(1f), style = HType.plex(17, FontWeight.Bold), color = HColors.ink)
            record.stamp?.let { StampImage(it, 44.dp) }
        }
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
=======
/** 이 달의 날짜를 주 단위(일요일 시작)로 나눠요. 빈칸은 null. */
private fun weeks(month: YearMonth): List<List<LocalDate?>> {
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value % 7 // 일요일 0
    val days = MutableList<LocalDate?>(leading) { null }
    days += (1..month.lengthOfMonth()).map { month.atDay(it) }
    while (days.size % 7 != 0) days += null
    return days.chunked(7)
}

@Composable
private fun DayCell(day: LocalDate, today: LocalDate, record: DayRecord?, isSelected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val isToday = day == today
    val isFuture = day.isAfter(today)
    Column(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(if (isSelected) HColors.ink else HColors.bg, shape)
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, HColors.ink, shape) else Modifier)
            // 기록이 있는 날만 고를 수 있어요.
            .plainClickable(enabled = record != null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(
            "${day.dayOfMonth}",
            style = plex(15, if (record != null) FontWeight.Bold else FontWeight.Medium),
            color = when {
                isSelected -> HColors.onAccent
                isFuture -> HColors.textDisabled
                else -> HColors.ink
            },
        )
        // 글자가 없어도 높이를 맞춰요. 유형 이름을 그대로 써요 (그림·사진·글·음성, 사용자 요청)
        Text(
            record?.type?.displayName ?: " ",
            style = plex(10, FontWeight.SemiBold),
            color = if (isSelected) HColors.onAccent else HColors.textSecondary,
        )
    }
}

/** 테두리 line · 모서리 20 · 안쪽 18 · 간격 10 */
@Composable
private fun SelectedCard(record: DayRecord, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, HColors.line, RoundedCornerShape(HRadius.card))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("${record.date.monthValue}월 ${record.date.dayOfMonth}일 · ${record.type.displayName}", style = HType.caption13, color = HColors.textSecondary)
        Text(record.topic, style = plex(17, FontWeight.Bold), color = HColors.ink)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${record.doneCount} / ${record.memberCount}명 참여", style = HType.caption13, color = HColors.textSecondary)
            Spacer(Modifier.weight(1f))
            if (record.didSubmit) {
                Box(
                    Modifier
                        .height(36.dp)
                        .background(HColors.surface, CircleShape)
                        .plainClickable(onClick = onOpen)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("모아보기", style = plex(13, FontWeight.SemiBold), color = HColors.ink) }
            } else {
                // Figma 에 없는 상태라 버튼 자리에 안내 글자만 둬요 (iOS 와 같음).
                Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
                    Text("내가 올리지 않은 날이라 볼 수 없어요", style = HType.caption13, color = HColors.textTertiary)
                }
>>>>>>> feat/ios-step1
            }
        }
    }
}
