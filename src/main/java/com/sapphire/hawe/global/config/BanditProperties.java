package com.sapphire.hawe.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 미션 틀 선택 밴딧 설정(TODO_2/D2~D4, AI_FEATURES.md §4). D2 는 보상 가중치만 쓰고, 나머지는 D3·D4 가 쓴다.
 * 기본값의 근거는 시뮬레이션 보고서 {@code ml/bandit-sim/REPORT.md}.
 *
 * @param rewardBase        제출하면 받는 기본 보상(미제출 0)
 * @param rewardEffort      정성(effort 0~1) 가중치
 * @param rewardEngage      반응·댓글(engage 0~1) 가중치. 세 가중치 합이 1 이라야 멤버 보상이 0~1
 * @param kappa             전역 → 그룹 수축 강도. 계획 초안 10 이었으나 D1 시뮬레이션에서 2 가 가장 좋았다
 * @param kSyn              합성 prior 의사 관측 상한(팔당). D1 결과 이득이 없어 합성 시드는 만들지 않았다(있으면 이 값까지만)
 * @param epsilon           탐색 하한: 어떤 후보도 ε/후보수 이상
 * @param repeatPenalty     최근 repeatDays 안에 나온 틀의 표본값 배율
 * @param repeatDays        반복 패널티를 보는 날 수
 * @param halfLifeDays      관측 감쇠 반감기(일)
 * @param shareCap          한 틀의 최근 shareWindowDays 일 점유율 상한
 * @param shareWindowDays   점유율 창(일)
 */
@ConfigurationProperties(prefix = "hawe.bandit")
public record BanditProperties(Double rewardBase, Double rewardEffort, Double rewardEngage, Double kappa, Double kSyn,
        Double epsilon, Double repeatPenalty, Integer repeatDays, Double halfLifeDays, Double shareCap,
        Integer shareWindowDays) {

    public BanditProperties {
        rewardBase = rewardBase == null ? 0.6 : rewardBase;
        rewardEffort = rewardEffort == null ? 0.2 : rewardEffort;
        rewardEngage = rewardEngage == null ? 0.2 : rewardEngage;
        kappa = kappa == null ? 2.0 : kappa;
        kSyn = kSyn == null ? 10.0 : kSyn;
        epsilon = epsilon == null ? 0.05 : epsilon;
        repeatPenalty = repeatPenalty == null ? 0.85 : repeatPenalty;
        repeatDays = repeatDays == null ? 3 : repeatDays;
        halfLifeDays = halfLifeDays == null ? 30.0 : halfLifeDays;
        shareCap = shareCap == null ? 0.4 : shareCap;
        shareWindowDays = shareWindowDays == null ? 7 : shareWindowDays;
        if (rewardBase < 0 || rewardEffort < 0 || rewardEngage < 0
                || Math.abs(rewardBase + rewardEffort + rewardEngage - 1.0) > 1e-9)
            throw new IllegalArgumentException("hawe.bandit.reward-* 는 0 이상이고 합이 1 이어야 함");
        if (kappa < 0 || kSyn < 0 || kSyn > 10 || epsilon < 0 || epsilon > 1 || repeatPenalty <= 0 || repeatPenalty > 1
                || repeatDays < 0 || halfLifeDays <= 0 || shareCap <= 0 || shareCap > 1 || shareWindowDays < 1)
            throw new IllegalArgumentException("hawe.bandit 값이 범위를 벗어남");
    }
}
