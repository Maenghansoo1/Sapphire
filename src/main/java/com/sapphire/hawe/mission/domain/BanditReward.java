package com.sapphire.hawe.mission.domain;

/**
 * 닫힌 미션 한 건의 밴딧 보상(TODO_2/D2, AI_FEATURES.md §4.2). [eligible] = 대상 인원 n, [total] = 멤버 보상 합 R (0 ≤ R ≤ n).
 * 갱신은 그 틀의 α += R, β += n − R. 대상 인원이 0 이면 통계에는 반영하지 않고 "반영함"만 기록한다.
 */
public record BanditReward(long missionId, long groupId, String template, long eligible, double total) {
    public BanditReward {
        if (eligible < 0 || total < 0 || total > eligible + 1e-9)
            throw new IllegalArgumentException("보상 합은 0 이상 대상 인원 이하여야 함");
    }

    public double alpha() { return total; }

    public double beta() { return eligible - total; }
}
