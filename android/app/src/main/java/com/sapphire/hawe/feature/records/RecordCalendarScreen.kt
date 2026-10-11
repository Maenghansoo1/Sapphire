package com.sapphire.hawe.feature.records

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
            }
        }
    }
}

/** 화살표 20 (44 칸) + "2026년 10월" 18 Bold */
@Composable
private fun MonthHeader(month: YearMonth, canGoBack: Boolean, canGoForward: Boolean, onMove: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MonthButton(HIcon.ChevronLeft, canGoBack) { onMove(-1) }
        Spacer(Modifier.weight(1f))
        Text("${month.year}년 ${month.monthValue}월", style = plex(18, FontWeight.Bold), color = HColors.ink)
        Spacer(Modifier.weight(1f))
        MonthButton(HIcon.ChevronRight, canGoForward) { onMove(1) }
    }
}

@Composable
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
                }
            }
        }
    }
}

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
            }
        }
    }
}
