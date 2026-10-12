package com.sapphire.hawe.core.net

import kotlinx.serialization.Serializable

@Serializable internal data class SubmissionAuthorDto(val userId: Long, val nickname: String,
    val characterKey: String? = null, val profileImageUrl: String? = null)

@Serializable internal data class SubmissionDto(val id: Long, val missionId: Long,
    val author: SubmissionAuthorDto, val template: String, val mediaUrl: String? = null,
    val mediaType: String? = null, val duration: Double? = null, val waveform: List<Float>? = null,
    val caption: String? = null, val text: String? = null, val submittedAt: String,
    val isLate: Boolean = false)

@Serializable internal data class SubmissionPageDto(val content: List<SubmissionDto>, val page: Int,
    val size: Int, val totalPages: Int, val totalElements: Long)
