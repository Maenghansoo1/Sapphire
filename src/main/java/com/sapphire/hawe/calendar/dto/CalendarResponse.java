package com.sapphire.hawe.calendar.dto;

import java.time.YearMonth;
import java.util.List;

public record CalendarResponse(YearMonth month, List<CalendarDay> days) {
}
