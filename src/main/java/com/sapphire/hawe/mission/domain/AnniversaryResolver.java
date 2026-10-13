package com.sapphire.hawe.mission.domain;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 오늘부터 {@link #LOOKAHEAD_DAYS}일 안의 특별한 날을 가까운 순으로 찾는다. 날짜 세기는 시작일을 1일째로 한다
 * (연인 앱의 "D+1"). 주년은 시작일의 월·일이 같은 날이다 (2월 29일 시작은 평년에 2월 28일).
 */
public final class AnniversaryResolver {

    public static final int LOOKAHEAD_DAYS = 7;

    private AnniversaryResolver() {
    }

    /**
     * @param coupleStart  연인 그룹의 "우리의 1일" (연인이 아니거나 없으면 무시)
     * @param groupStart   그룹을 만든 날
     * @param calendarDays 설정한 특별한 날 (월·일 → 이름)
     */
    public static List<SpecialDay> resolve(String relationType, LocalDate coupleStart, LocalDate groupStart,
            LocalDate today, Map<MonthDay, String> calendarDays) {
        List<SpecialDay> days = new ArrayList<>();
        for (int away = 0; away <= LOOKAHEAD_DAYS; away++) {
            LocalDate date = today.plusDays(away);
            if ("COUPLE".equals(relationType) && coupleStart != null) {
                Optional<String> name = milestone(coupleStart, date);
                if (name.isPresent()) days.add(new SpecialDay(SpecialDay.Kind.COUPLE_MILESTONE, "우리의 " + name.get(), away));
            }
            if (groupStart != null) {
                Optional<String> name = milestone(groupStart, date);
                if (name.isPresent()) days.add(new SpecialDay(SpecialDay.Kind.GROUP_MILESTONE, "우리 그룹 " + name.get(), away));
            }
            String calendar = calendarDays.get(MonthDay.from(date));
            if (calendar != null) days.add(new SpecialDay(SpecialDay.Kind.CALENDAR, calendar, away));
        }
        return List.copyOf(days);
    }

    /** 100일 단위면 "N일", 주년이면 "N주년", 아니면 비어 있음. */
    static Optional<String> milestone(LocalDate start, LocalDate date) {
        if (date.isBefore(start)) return Optional.empty();
        long dayNumber = ChronoUnit.DAYS.between(start, date) + 1;
        if (dayNumber % 100 == 0) return Optional.of(dayNumber + "일");
        int years = date.getYear() - start.getYear();
        if (years >= 1 && start.plusYears(years).equals(date)) return Optional.of(years + "주년");
        return Optional.empty();
    }
}
