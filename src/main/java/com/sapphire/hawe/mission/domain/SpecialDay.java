package com.sapphire.hawe.mission.domain;

/** 오늘(daysAway 0) 또는 앞으로 며칠 안에 오는 특별한 날. AI 가 미션 소재로 쓸 수 있다. */
public record SpecialDay(Kind kind, String name, int daysAway) {

    public enum Kind {
        /** 연인의 "우리의 1일" 기준 100일 단위·주년. */
        COUPLE_MILESTONE,
        /** 그룹을 만든 날 기준 100일 단위·주년. */
        GROUP_MILESTONE,
        /** 설정 {@code hawe.mission.ai.special-days} 에 적은 날 (예: 크리스마스). */
        CALENDAR
    }
}
