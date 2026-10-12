package com.sapphire.hawe.core.model

import java.time.Instant

/** 달력에 찍히는 내 도장. 서버가 제출 기록으로 계산해서 내려줘요. */
enum class Stamp(val displayName: String) {
    GOOD("참잘했어요"), LATE("지각"), PENALTY("벌점")
}

data class DayRecord(
    val date: Instant, val type: MissionType, val topic: String,
    val doneCount: Int, val memberCount: Int, val didSubmit: Boolean, val missionID: Long? = null,
    val stamp: Stamp? = null,
)
