package com.sapphire.hawe.mission.dto;

import com.sapphire.hawe.mission.domain.ExperimentStats.Interval;

import java.time.LocalDate;
import java.util.List;

/**
 * AI 주제 vs 랜덤 주제 비교 리포트(B9). 비율(참여율 등)은 0~1, 차이는 %p. 사람·그룹을 알아볼 값은 없다.
 *
 * @param ai                 배정대로 센 AI 군 (AI 실패로 고정 문장이 나간 미션 포함 — intention-to-treat)
 * @param aiPerProtocol      AI 가 실제로 문장을 만든 미션만
 * @param paired             그룹 안에서 AI 날과 랜덤 날을 비교 (주 지표)
 */
public record ExperimentReport(LocalDate from, LocalDate to, boolean underpowered, List<String> warnings,
        ArmSummary ai, ArmSummary random, Comparison difference,
        ArmSummary aiPerProtocol, Comparison perProtocolDifference, int aiFallbackMissions, Paired paired) {

    /**
     * @param participation             제출 합 ÷ 대상 인원 합
     * @param groupDayMeanParticipation (그룹, 날짜)마다 참여율을 낸 뒤 평균 — 하루 미션 수가 다른 그룹의 무게를 맞춘다
     * @param onTimeRate                마감 전 제출 ÷ 제출
     * @param engagementPerMission      제출물에 달린 반응+댓글 수 ÷ 미션 수
     * @param medianFirstSubmitMinutes  미션이 열린 뒤 첫 제출까지(제출이 있는 미션만)의 중앙값
     */
    public record ArmSummary(int missions, int groups, int groupDays, long eligible, long submitted,
            Double participation, Interval participationCi95, Double groupDayMeanParticipation, Double onTimeRate,
            Double engagementPerMission, Double medianFirstSubmitMinutes) {
    }

    /** 참여율 차이(AI − 랜덤)와 두 비율 z 검정. 같은 그룹 미션은 서로 닮아 p 가 실제보다 작게 나온다. */
    public record Comparison(Double differencePoints, Double z, Double pValue) {
    }

    /**
     * @param groups               AI 날과 랜덤 날을 모두 겪은 그룹 수
     * @param excludedGroups       한쪽 군만 겪어서 뺀 그룹 수
     * @param meanDifferencePoints 그룹별 (AI 날 평균 참여율 − 랜덤 날 평균 참여율)의 평균, %p
     * @param ci95                 t 분포 95% 구간 (%p)
     * @param bootstrapCi95        그룹이 30개 미만일 때 보조 부트스트랩 95% 구간 (%p, 5,000번·고정 시드)
     */
    public record Paired(int groups, int excludedGroups, Double meanDifferencePoints, Interval ci95,
            Interval bootstrapCi95, int positive, int negative, int ties, Double signTestP) {
    }
}
