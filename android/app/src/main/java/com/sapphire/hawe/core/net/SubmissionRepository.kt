package com.sapphire.hawe.core.net

import com.sapphire.hawe.core.data.SubmissionContent
import com.sapphire.hawe.core.model.AvatarContent
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.Profile
import com.sapphire.hawe.core.model.Reaction
import com.sapphire.hawe.core.model.Sticker
import com.sapphire.hawe.core.model.Submission
import com.sapphire.hawe.core.model.SubmissionComment
import com.sapphire.hawe.designsystem.assets.ProfileCharacter
import java.net.URI
import java.time.OffsetDateTime
import java.util.Locale

fun voiceDurationParam(value: Double): String = String.format(Locale.ROOT, "%.2f",
    (if (value.isFinite()) value else 0.1).coerceIn(0.1, 30.0))

fun waveformParam(values: List<Float>): String {
    val sampled = if (values.size <= 64) values else (0 until 64).map { values[it * (values.size - 1) / 63] }
    return sampled.joinToString(prefix = "[", postfix = "]", separator = ",") {
        (if (it.isFinite()) it else 0f).coerceIn(0f, 1f).toString()
    }
}

internal fun templateType(template: String): MissionType? = when {
    template.startsWith("PHOTO_") -> MissionType.PHOTO
    template.startsWith("DRAW_") -> MissionType.DRAW
    template.startsWith("VOICE_") -> MissionType.VOICE
    template.startsWith("TEXT_") -> MissionType.WRITE
    else -> null
}

sealed interface SubmitOutcome {
    data class Stay(val message: String) : SubmitOutcome
    data object AlreadySubmitted : SubmitOutcome
    data object MissionClosed : SubmitOutcome
    data object ReturnHome : SubmitOutcome
}

fun classifySubmitFailure(error: Throwable): SubmitOutcome = when (error) {
    is NetworkException -> SubmitOutcome.Stay("네트워크 연결을 확인해 주세요")
    is ApiException -> when (error.code) {
        "ALREADY_SUBMITTED" -> SubmitOutcome.AlreadySubmitted
        "MISSION_CLOSED" -> SubmitOutcome.MissionClosed
        "INVALID_SUBMISSION" -> SubmitOutcome.Stay(error.message ?: "제출 내용을 확인해 주세요")
        "FILE_TOO_LARGE" -> SubmitOutcome.Stay("파일이 너무 커요")
        "UNSUPPORTED_FILE_TYPE" -> SubmitOutcome.Stay("지원하지 않는 파일이에요")
        "NOT_GROUP_MEMBER", "MISSION_NOT_FOUND" -> SubmitOutcome.ReturnHome
        else -> SubmitOutcome.Stay("잠시 후 다시 시도해 주세요")
    }
    else -> SubmitOutcome.Stay("잠시 후 다시 시도해 주세요")
}

class SubmissionRepository(private val api: ApiClient) {
    suspend fun submit(missionId: Long, content: SubmissionContent, myUserId: Long): Submission {
        val parts = when (content) {
            is SubmissionContent.Image -> {
                val png = content.fileExtension.equals("png", ignoreCase = true)
                listOf(ApiClient.MultipartPart.Bytes("file", if (png) "drawing.png" else "photo.jpg",
                    if (png) "image/png" else "image/jpeg", content.bytes))
            }
            is SubmissionContent.Text -> listOf(ApiClient.MultipartPart.Text("text", content.body))
            is SubmissionContent.Voice -> listOf(
                ApiClient.MultipartPart.FilePart("file", "voice.m4a", "audio/mp4", content.file),
                ApiClient.MultipartPart.Text("duration", voiceDurationParam(content.duration)),
                ApiClient.MultipartPart.Text("waveform", waveformParam(content.waveform)))
        }
        val dto: SubmissionDto = api.postMultipart("/api/missions/$missionId/submissions", parts)
        return dto.toModel(myUserId) ?: throw ApiException(201, "INVALID_RESPONSE", "서버 응답을 읽을 수 없습니다")
    }

    suspend fun feed(missionId: Long, myUserId: Long): List<Submission> {
        val page: SubmissionPageDto = api.get("/api/missions/$missionId/submissions?page=0&size=100")
        return page.content.mapNotNull { it.toModel(myUserId) }
    }

    /** 스티커 토글. 서버가 돌려주는 이 작품의 반응 전체로 화면을 다시 그려요. */
    suspend fun toggleReaction(submissionId: Long, sticker: Sticker): List<Reaction> {
        val response: ReactionsDto = api.post("/api/submissions/$submissionId/reactions",
            ReactionRequestDto(sticker.key))
        return response.reactions.toReactions()
    }

    suspend fun comments(submissionId: Long): List<SubmissionComment> {
        val page: CommentPageDto = api.get("/api/submissions/$submissionId/comments?page=0&size=100")
        return page.content.map { it.toModel() }
    }

    suspend fun addComment(submissionId: Long, content: String, mentionedUserIds: List<Long>,
        image: ByteArray?, imageExtension: String = "jpg"): SubmissionComment {
        val parts = buildList<ApiClient.MultipartPart> {
            add(ApiClient.MultipartPart.Text("content", content))
            mentionedUserIds.distinct().forEach { add(ApiClient.MultipartPart.Text("mentionedUserIds", it.toString())) }
            if (image != null) {
                val png = imageExtension.equals("png", ignoreCase = true)
                add(ApiClient.MultipartPart.Bytes("file", if (png) "comment.png" else "comment.jpg",
                    if (png) "image/png" else "image/jpeg", image))
            }
        }
        val dto: CommentDto = api.postMultipart("/api/submissions/$submissionId/comments", parts)
        return dto.toModel()
    }

    suspend fun deleteComment(commentId: Long) = api.delete("/api/comments/$commentId")

    private fun List<ReactionDto>.toReactions(): List<Reaction> = mapNotNull { dto ->
        Sticker.fromKey(dto.stickerType)?.let { Reaction(it, dto.count, dto.reactedByMe, dto.userIds) }
    }

    private fun CommentDto.toModel() = SubmissionComment(id,
        Profile(AvatarContent.Character(ProfileCharacter.fromKey(author.characterKey)), author.nickname),
        author.userId, content, imageUrl?.let { URI(api.absoluteUrl(it)) }, mentionedUserIds,
        OffsetDateTime.parse(createdAt).toInstant(), mine)

    private fun SubmissionDto.toModel(myUserId: Long): Submission? {
        val type = templateType(template) ?: return null
        return Submission(id, Profile(AvatarContent.Character(ProfileCharacter.fromKey(author.characterKey)),
            author.nickname), author.userId == myUserId, type,
            OffsetDateTime.parse(submittedAt).toInstant(),
            mediaUrl?.let { URI(api.absoluteUrl(it)) }, text, duration, waveform.orEmpty(), isLate,
            author.userId, reactions.orEmpty().toReactions(), commentCount)
    }
}
