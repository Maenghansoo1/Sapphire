package com.sapphire.hawe.feature.records

import com.sapphire.hawe.core.HaweTime
import com.sapphire.hawe.core.model.DayRecord
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

val WEEKDAY_LABELS = listOf("일", "월", "화", "수", "목", "금", "토")

fun seoulDate(instant: Instant): LocalDate = instant.atZone(HaweTime.SEOUL).toLocalDate()

/** 날짜(서울) → 기록. 같은 날 기록이 둘이면 앞의 것을 써요. */
fun recordsByDate(records: List<DayRecord>): Map<LocalDate, DayRecord> {
    val result = LinkedHashMap<LocalDate, DayRecord>()
    records.forEach { result.putIfAbsent(seoulDate(it.date), it) }
    return result
}

/** 이 달의 날짜를 일요일 시작 주 단위로 나눠요. 빈칸은 null. */
fun monthWeeks(month: YearMonth): List<List<LocalDate?>> {
    val leading = month.atDay(1).dayOfWeek.value % 7 // 일=0 … 토=6
    val days = MutableList<LocalDate?>(leading) { null }
    for (day in 1..month.lengthOfMonth()) days += month.atDay(day)
    while (days.size % 7 != 0) days += null
    return days.chunked(7)
}

fun monthTitle(month: YearMonth): String = "${month.year}년 ${month.monthValue}월"

fun firstMonth(createdAt: Instant): YearMonth = YearMonth.from(seoulDate(createdAt))
fun currentMonth(now: Instant): YearMonth = YearMonth.from(seoulDate(now))

fun canGoPrevious(month: YearMonth, first: YearMonth) = month.isAfter(first)
fun canGoNext(month: YearMonth, last: YearMonth) = month.isBefore(last)

/** 처음에는 가장 최근 기록이 있는 날을 골라 둬요. */
fun initialSelectedDate(records: Map<LocalDate, DayRecord>): LocalDate? = records.keys.maxOrNull()

fun cellDescription(date: LocalDate, record: DayRecord?, isToday: Boolean): String {
    var text = "${date.monthValue}월 ${date.dayOfMonth}일"
    if (isToday) text += ", 오늘"
    if (record != null) text += ", ${record.type.displayName} 기록"
    return text
}

const val NOT_SUBMITTED_NOTICE = "내가 올리지 않은 날이라 볼 수 없어요"
