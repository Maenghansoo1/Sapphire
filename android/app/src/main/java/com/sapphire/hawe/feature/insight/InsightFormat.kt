package com.sapphire.hawe.feature.insight

import java.time.YearMonth
import kotlin.math.ceil
import kotlin.math.roundToInt

fun canShowPreviousInsight(month: YearMonth, current: YearMonth) = month.isAfter(current.minusMonths(12))
fun canShowNextInsight(month: YearMonth, current: YearMonth) = month.isBefore(current)
fun insightPercent(rate: Double): Int = (rate * 100).roundToInt().coerceIn(0, 100)
fun insightDelta(delta: Int?): String? = when {
    delta == null -> null
    delta > 0 -> "+$delta"
    delta < 0 -> "$delta"
    else -> "변화 없음"
}
fun insightResponseTime(seconds: Long): String {
    val minutes = ceil(seconds.coerceAtLeast(0) / 60.0).toLong().coerceAtLeast(1)
    return if (minutes < 60) "보통 ${minutes}분 안에" else "보통 ${minutes / 60}시간 ${minutes % 60}분 안에"
}
fun insightWordSize(count: Int, maximum: Int): Int = when {
    count * 3 >= maximum * 2 -> 18
    count * 3 >= maximum -> 15
    else -> 13
}
