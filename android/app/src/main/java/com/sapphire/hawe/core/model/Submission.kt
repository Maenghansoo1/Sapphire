package com.sapphire.hawe.core.model

import java.net.URI
import java.time.Instant

data class Submission(
    val id: Long, val author: Profile, val isMine: Boolean, val type: MissionType,
    val createdAt: Instant, val mediaURL: URI? = null, val text: String? = null,
    val duration: Double? = null, val waveform: List<Float> = emptyList(),
    val isLate: Boolean = false, val authorID: Long? = null,
)

data class MissionResult(
    val id: Long, val groupName: String, val type: MissionType, val topic: String,
    val date: Instant, val memberCount: Int, val submissions: List<Submission>, val pending: List<Profile>,
)
