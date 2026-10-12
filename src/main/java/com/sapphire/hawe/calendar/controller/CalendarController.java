package com.sapphire.hawe.calendar.controller;

import com.sapphire.hawe.calendar.dto.CalendarResponse;
import com.sapphire.hawe.calendar.dto.MyStatsResponse;
import com.sapphire.hawe.calendar.service.CalendarService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/api")
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping("/groups/{groupId}/calendar")
    public CalendarResponse getCalendar(@AuthenticationPrincipal Long userId, @PathVariable Long groupId,
            @RequestParam YearMonth month) {
        return calendarService.getCalendar(userId, groupId, month);
    }

    @GetMapping("/me/stats")
    public MyStatsResponse getMyStats(@AuthenticationPrincipal Long userId) {
        return calendarService.getMyStats(userId);
    }
}
