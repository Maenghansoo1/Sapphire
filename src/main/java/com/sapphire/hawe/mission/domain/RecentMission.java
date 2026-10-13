package com.sapphire.hawe.mission.domain;

/** 그룹에 최근 열린 미션 하나 (문장 연속 방지와 AI 입력용). {@code template} 은 틀 코드. */
public record RecentMission(String content, String category, String template) {
}
