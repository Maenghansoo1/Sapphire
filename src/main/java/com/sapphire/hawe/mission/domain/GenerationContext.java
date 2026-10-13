package com.sapphire.hawe.mission.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * 미션 문장을 만들 때 쓰는 그룹 정보. 고정 문장 생성기는 최근 문장만 보고, AI 생성기는 전부 쓴다.
 *
 * @param relationType   그룹 관계 코드 (COUPLE·FAMILY·FRIEND·RIVAL·CASUAL)
 * @param recentMissions 피해야 할 최근 미션, 최근 것부터 (같은 날 먼저 열린 슬롯 포함)
 */
public record GenerationContext(LocalDate date, String relationType, List<RecentMission> recentMissions,
        List<SpecialDay> specialDays, GroupSignals signals) {

    public GenerationContext {
        recentMissions = List.copyOf(recentMissions);
        specialDays = List.copyOf(specialDays);
    }

    public List<String> recentContents() {
        return recentMissions.stream().map(RecentMission::content).toList();
    }

    public GenerationContext withRecentMissions(List<RecentMission> recent) {
        return new GenerationContext(date, relationType, recent, specialDays, signals);
    }
}
