package com.sapphire.hawe.mission.domain;

/** 주제 비교 실험(B9)의 군. (그룹, 날짜)마다 하나가 정해지고 같은 날 슬롯은 모두 같은 군이다. 앱에는 내보내지 않는다. */
public enum ExperimentArm {
    /** AI 가 만든 주제 (실패하면 고정 문장이 나가도 이 군으로 센다 — intention-to-treat). */
    AI,
    /** 고정 문장 풀에서 고른 주제. */
    RANDOM
}
