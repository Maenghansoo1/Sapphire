package com.sapphire.hawe.calendar.dto;

import java.time.LocalDate;
import java.util.List;

public record CalendarDay(LocalDate date, List<CalendarMission> missions) {
}
