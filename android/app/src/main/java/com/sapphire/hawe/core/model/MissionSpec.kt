package com.sapphire.hawe.core.model

data class MissionSpec(
    val seconds: Int? = null, val word: String? = null,
    val drawFrom: String? = null, val drawTo: String? = null,
    // 밸런스 게임(TEXT_BALANCE)의 두 선택지
    val optionA: String? = null, val optionB: String? = null,
    // 슬라이더: 질문(SLIDER_POSITION·SLIDER_CONSENSUS) 또는 주제(SLIDER_TASTE)와 양 끝 라벨
    val question: String? = null, val topic: String? = null,
    val left: String? = null, val right: String? = null,
) {
    val sliderQuestion: String? get() = question ?: topic
}
