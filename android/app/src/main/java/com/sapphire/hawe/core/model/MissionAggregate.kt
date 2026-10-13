package com.sapphire.hawe.core.model

/** 집계 틀(밸런스·슬라이더)의 그룹 결과. 서버가 계산한 값을 그대로 담아요(`MISSIONS_API.md` §5). */
sealed interface MissionAggregate {
    val totalCount: Int

    data class Balance(override val totalCount: Int, val countA: Int, val countB: Int,
        val myChoice: String?) : MissionAggregate

    /** SLIDER_POSITION·SLIDER_TASTE. `diffFromAverage` = 내 값 − 평균. */
    data class Slider(override val totalCount: Int, val average: Double?, val distribution: List<SliderPoint>,
        val myNumber: Int?, val diffFromAverage: Double?) : MissionAggregate

    /** SLIDER_CONSENSUS. `ranking` 은 서버 순서(차이 작은 순), 동률 승자는 여러 명. */
    data class Consensus(override val totalCount: Int, val average: Double?, val ranking: List<ConsensusRank>,
        val winnerUserIds: Set<Long>) : MissionAggregate
}

data class SliderPoint(val userId: Long, val nickname: String, val number: Int)
data class ConsensusRank(val userId: Long, val nickname: String, val guess: Int, val diff: Double)
