package com.sapphire.hawe.mission.domain;

/**
 * 틀(팔) 하나의 Beta 통계(TODO_2/D2). 실측(real)과 합성 prior(syn)를 따로 둔다 — 합성은 실측과 섞어 보고하지 않는다.
 * 행이 없는 새 틀은 {@link #EMPTY} 와 같다(D4 에서 Beta(1,1) 로 시작).
 */
public record BanditArmStats(double alphaReal, double betaReal, double alphaSyn, double betaSyn, int missionCount) {
    public static final BanditArmStats EMPTY = new BanditArmStats(0, 0, 0, 0, 0);
}
