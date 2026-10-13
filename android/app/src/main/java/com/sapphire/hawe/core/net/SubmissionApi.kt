package com.sapphire.hawe.core.net

import kotlinx.serialization.Serializable

@Serializable internal data class SubmissionAuthorDto(val userId: Long, val nickname: String,
    val characterKey: String? = null, val profileImageUrl: String? = null)

@Serializable internal data class SubmissionDto(val id: Long, val missionId: Long,
    val author: SubmissionAuthorDto, val template: String, val mediaUrl: String? = null,
    val mediaType: String? = null,
    val caption: String? = null, val text: String? = null, val number: Int? = null,
    val choice: String? = null, val guess: Int? = null, val submittedAt: String,
    val isLate: Boolean = false, val reactions: List<ReactionDto>? = null, val commentCount: Int = 0)

@Serializable internal data class ReactionDto(val stickerType: String, val count: Int = 0,
    val reactedByMe: Boolean = false, val userIds: List<Long> = emptyList())

@Serializable internal data class ReactionsDto(val reactions: List<ReactionDto> = emptyList())

@Serializable internal data class ReactionRequestDto(val stickerType: String)

@Serializable internal data class CommentDto(val id: Long, val author: SubmissionAuthorDto,
    val content: String = "", val imageUrl: String? = null, val mentionedUserIds: List<Long> = emptyList(),
    val createdAt: String, val mine: Boolean = false)

@Serializable internal data class CommentPageDto(val content: List<CommentDto>, val page: Int,
    val size: Int, val totalPages: Int, val totalElements: Long)

@Serializable internal data class SubmissionPageDto(val content: List<SubmissionDto>, val page: Int,
    val size: Int, val totalPages: Int, val totalElements: Long)

@Serializable internal data class MissionResultDto(val template: String, val totalCount: Int = 0,
    val choices: List<ChoiceCountDto>? = null, val myChoice: String? = null,
    val average: Double? = null, val distribution: List<NumberEntryDto>? = null, val myNumber: Int? = null,
    val diffFromAverage: Double? = null, val ranking: List<RankingEntryDto>? = null,
    val winnerUserIds: List<Long>? = null)

@Serializable internal data class ChoiceCountDto(val choice: String, val count: Int = 0)
@Serializable internal data class NumberEntryDto(val userId: Long, val nickname: String = "", val number: Int)
@Serializable internal data class RankingEntryDto(val userId: Long, val nickname: String = "", val guess: Int,
    val diff: Double)
