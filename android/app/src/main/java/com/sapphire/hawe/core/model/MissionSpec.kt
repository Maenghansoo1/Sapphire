package com.sapphire.hawe.core.model

data class MissionSpec(
    val seconds: Int? = null, val word: String? = null,
    val drawFrom: String? = null, val drawTo: String? = null,
    // 밸런스 게임(TEXT_BALANCE)의 두 선택지
    val optionA: String? = null, val optionB: String? = null,
    // 그림·사진 틀이 그릴(찍을) 대상(`subject`), 사람을 지목한 미션인지(`target`, 서버가 닉네임으로 풀어 줌)
    val subject: String? = null, val hasTarget: Boolean = false,
)
