package com.sapphire.hawe.core.model

import java.net.URI
import java.time.Instant
import java.util.UUID

data class SubmissionComment(
    val id: UUID, val author: Profile, val isMine: Boolean, val body: String, val createdAt: Instant,
)

data class Submission(
    val id: UUID, val author: Profile, val isMine: Boolean, val type: MissionType,
    val createdAt: Instant, val mediaURL: URI? = null, val text: String? = null,
    val duration: Double? = null, val waveform: List<Float> = emptyList(),
    val likeCount: Int = 0, val likedByMe: Boolean = false,
    val likerNames: List<String> = emptyList(),
    val comments: List<SubmissionComment> = emptyList(), val authorID: UUID? = null,
)

data class MissionResult(
    val id: UUID, val groupName: String, val type: MissionType, val topic: String,
    val date: Instant, val memberCount: Int, val submissions: List<Submission>, val pending: List<Profile>,
)
