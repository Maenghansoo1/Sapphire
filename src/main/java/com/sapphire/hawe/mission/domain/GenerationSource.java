package com.sapphire.hawe.mission.domain;

/** 미션 문장을 누가 만들었는지. B9 의 AI 주제 vs 랜덤 주제 비교에 쓴다 (앱 응답에는 내보내지 않는다). */
public enum GenerationSource {
    /** AI 가 만들고 서버 검증을 통과했다. */
    AI,
    /** AI 를 시도했지만 실패해서 고정 문장을 썼다. */
    AI_FALLBACK,
    /** AI 를 쓰지 않고 고정 문장을 썼다. */
    PLACEHOLDER
}
