package com.sapphire.hawe.core.model

/** 집계 틀(밸런스 게임)의 그룹 결과. 서버가 계산한 값을 그대로 담아요(`MISSIONS_API.md` §5). */
sealed interface MissionAggregate {
    val totalCount: Int

    data class Balance(override val totalCount: Int, val countA: Int, val countB: Int,
        val myChoice: String?) : MissionAggregate
}
