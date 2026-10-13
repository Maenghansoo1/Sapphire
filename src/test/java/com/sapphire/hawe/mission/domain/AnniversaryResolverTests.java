package com.sapphire.hawe.mission.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnniversaryResolverTests {

    private static final LocalDate COUPLE_START = LocalDate.of(2026, 1, 1);   // 1일째
    private static final LocalDate GROUP_START = LocalDate.of(2026, 9, 1);

    @Test void coupleHundredDaysCountFromDayOne() {
        LocalDate hundredth = COUPLE_START.plusDays(99);  // 2026-04-10 = 100일째
        assertEquals(List.of(new SpecialDay(SpecialDay.Kind.COUPLE_MILESTONE, "우리의 100일", 0)), couple(hundredth));
        assertEquals(List.of(new SpecialDay(SpecialDay.Kind.COUPLE_MILESTONE, "우리의 200일", 0)),
                couple(COUPLE_START.plusDays(199)));
        // 7일 전부터 미리 알려 주고, 8일 전과 지난 뒤에는 없다
        assertEquals(List.of(new SpecialDay(SpecialDay.Kind.COUPLE_MILESTONE, "우리의 100일", 7)), couple(hundredth.minusDays(7)));
        assertTrue(couple(hundredth.minusDays(8)).isEmpty());
        assertTrue(couple(hundredth.plusDays(1)).isEmpty());
    }

    @Test void coupleAnniversariesFollowTheStartDate() {
        assertEquals(List.of(new SpecialDay(SpecialDay.Kind.COUPLE_MILESTONE, "우리의 1주년", 0)),
                couple(LocalDate.of(2027, 1, 1)));
        assertEquals(List.of(new SpecialDay(SpecialDay.Kind.COUPLE_MILESTONE, "우리의 2주년", 3)),
                couple(LocalDate.of(2027, 12, 29)));
        // 2월 29일에 시작했으면 평년에는 2월 28일이 주년
        LocalDate leap = LocalDate.of(2024, 2, 29);
        assertEquals(List.of("우리의 1주년"), names(AnniversaryResolver.resolve("COUPLE", leap, null,
                LocalDate.of(2025, 2, 28), Map.of())));
        assertTrue(AnniversaryResolver.resolve("COUPLE", leap, null, LocalDate.of(2025, 3, 1), Map.of()).isEmpty());
        assertEquals(List.of("우리의 4주년"), names(AnniversaryResolver.resolve("COUPLE", leap, null,
                LocalDate.of(2028, 2, 29), Map.of())));
    }

    @Test void onlyCouplesGetCoupleMilestonesAndNothingBeforeTheStart() {
        LocalDate hundredth = COUPLE_START.plusDays(99);
        assertTrue(AnniversaryResolver.resolve("FRIEND", COUPLE_START, null, hundredth, Map.of()).isEmpty());
        assertTrue(AnniversaryResolver.resolve("COUPLE", null, null, hundredth, Map.of()).isEmpty());
        assertTrue(AnniversaryResolver.resolve("COUPLE", COUPLE_START, null, COUPLE_START.minusDays(30), Map.of()).isEmpty());
    }

    @Test void everyGroupGetsGroupMilestonesAndCalendarDays() {
        // 12-09 가 100일째. 크리스마스는 7일보다 멀어서 아직 없다
        List<SpecialDay> days = AnniversaryResolver.resolve("FRIEND", null, GROUP_START, GROUP_START.plusDays(99),
                Map.of(MonthDay.of(12, 25), "크리스마스"));
        assertEquals(List.of(new SpecialDay(SpecialDay.Kind.GROUP_MILESTONE, "우리 그룹 100일", 0)), days);
        List<SpecialDay> withCalendar = AnniversaryResolver.resolve("FRIEND", null, GROUP_START, LocalDate.of(2026, 12, 9),
                Map.of(MonthDay.of(12, 10), "첫눈 오는 날", MonthDay.of(12, 25), "크리스마스"));
        assertEquals(List.of(new SpecialDay(SpecialDay.Kind.GROUP_MILESTONE, "우리 그룹 100일", 0),
                new SpecialDay(SpecialDay.Kind.CALENDAR, "첫눈 오는 날", 1)), withCalendar);
        assertEquals(List.of("우리 그룹 1주년"), names(AnniversaryResolver.resolve("CASUAL", null, GROUP_START,
                LocalDate.of(2027, 9, 1), Map.of())));
        assertTrue(AnniversaryResolver.resolve("CASUAL", null, GROUP_START, GROUP_START, Map.of()).isEmpty());
    }

    @Test void couplesCanHaveBothKindsInDateOrder() {
        // 연인 100일(04-10)이 3일 뒤, 그룹 100일(오늘)
        LocalDate today = LocalDate.of(2026, 4, 7);
        LocalDate groupStart = today.minusDays(99);
        assertEquals(List.of(new SpecialDay(SpecialDay.Kind.GROUP_MILESTONE, "우리 그룹 100일", 0),
                new SpecialDay(SpecialDay.Kind.COUPLE_MILESTONE, "우리의 100일", 3)),
                AnniversaryResolver.resolve("COUPLE", COUPLE_START, groupStart, today, Map.of()));
    }

    private static List<SpecialDay> couple(LocalDate today) {
        return AnniversaryResolver.resolve("COUPLE", COUPLE_START, null, today, Map.of());
    }

    private static List<String> names(List<SpecialDay> days) {
        return days.stream().map(SpecialDay::name).toList();
    }
}
