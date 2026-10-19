package com.sapphire.hawe.mission.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 그룹 맞춤 주제를 위한 지난 기록 신호. 미션 단위 숫자만 담는다 — 사람 이름·답·댓글 내용은 없다.
 *
 * @param recentMissions     최근 닫힌 미션들의 참여 결과 (최근 것부터)
 * @param mediaParticipation 최근 30일 매체별 평균 참여율 (PHOTO·TEXT·DRAW 중 기록이 있는 것만)
 */
public record GroupSignals(int memberCount, long daysTogether, List<MissionStat> recentMissions,
        Map<String, Double> mediaParticipation) {

    public GroupSignals {
        recentMissions = List.copyOf(recentMissions);
        mediaParticipation = Collections.unmodifiableMap(new LinkedHashMap<>(mediaParticipation));  // 순서 유지
    }

    /** 첫 미션처럼 지난 기록이 없을 때. */
    public static GroupSignals none(int memberCount, long daysTogether) {
        return new GroupSignals(memberCount, daysTogether, List.of(), Map.of());
    }

    /**
     * @param participationRate       제출한 사람 ÷ 미션이 열릴 때 있던 사람 (0~1)
     * @param engagementPerSubmission 제출 하나당 반응+댓글 수
     */
    public record MissionStat(String template, String content, double participationRate,
            double engagementPerSubmission) {
    }
}
