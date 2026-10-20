package com.sapphire.hawe.feature.results

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sapphire.hawe.core.model.GroupMember
import com.sapphire.hawe.core.model.Reaction
import com.sapphire.hawe.core.model.Sticker
import com.sapphire.hawe.core.model.SubmissionComment
import com.sapphire.hawe.core.net.ApiException
import com.sapphire.hawe.core.net.NetworkException
import com.sapphire.hawe.core.net.SubmissionRepository
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.components.HAvatar
import com.sapphire.hawe.designsystem.components.MascotAvatar
import com.sapphire.hawe.core.model.AiDetail
import com.sapphire.hawe.designsystem.components.HTextField
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HRadius
import com.sapphire.hawe.designsystem.tokens.HType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_COMMENT_LENGTH = 300
private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024

internal fun socialErrorMessage(error: Throwable): String = when {
    error is NetworkException -> "네트워크 연결을 확인해 주세요"
    error is ApiException -> when (error.code) {
        "FEED_LOCKED" -> "먼저 미션을 올려 주세요"
        "NOT_GROUP_MEMBER", "SUBMISSION_NOT_FOUND", "COMMENT_NOT_FOUND" -> "더 이상 볼 수 없는 글이에요"
        "NOT_COMMENT_OWNER" -> "내 댓글만 지울 수 있어요"
        "FILE_TOO_LARGE" -> "사진이 너무 커요"
        "UNSUPPORTED_FILE_TYPE" -> "지원하지 않는 사진이에요"
        "VALIDATION_ERROR" -> "입력 내용을 확인해 주세요"
        else -> "잠시 후 다시 시도해 주세요"
    }
    else -> "잠시 후 다시 시도해 주세요"
}

/** 멘션으로 보낼 id: 입력창에 `@닉네임`이 아직 남아 있는 멤버만. */
internal fun mentionedIdsIn(text: String, picked: Map<Long, String>): List<Long> =
    picked.filter { (_, nickname) -> text.contains("@$nickname") }.keys.toList()

internal fun stickerToggleSequence(mine: List<Sticker>, pressed: Sticker): List<Sticker> {
    val selected = mine.distinct()
    return if (pressed in selected) selected else selected + pressed
}

@Composable
fun SubmissionSocial(submissionID: Long, initialReactions: List<Reaction>, members: List<GroupMember>,
    repository: SubmissionRepository) {
    val scope = rememberCoroutineScope()
    var reactions by remember(submissionID) { mutableStateOf(initialReactions) }
    var comments by remember(submissionID) { mutableStateOf<List<SubmissionComment>?>(null) }
    var loadFailed by remember(submissionID) { mutableStateOf(false) }
    var reload by remember(submissionID) { mutableIntStateOf(0) }
    var message by remember(submissionID) { mutableStateOf<String?>(null) }
    var toggling by remember(submissionID) { mutableStateOf(false) }
    val names = remember(members) { members.associate { it.id to it.displayed.nickname } }

    LaunchedEffect(submissionID, reload) {
        loadFailed = false
        try { comments = repository.comments(submissionID) }
        catch (error: Exception) {
            if (error is CancellationException) throw error
            loadFailed = true
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        StickerBar(reactions, names) { sticker ->
            if (toggling) return@StickerBar
            toggling = true
            val sequence = stickerToggleSequence(reactions.filter { it.reactedByMe }.map { it.sticker }, sticker)
            scope.launch {
                try {
                    sequence.forEach { next -> reactions = repository.toggleReaction(submissionID, next) }
                    message = null
                }
                catch (error: Exception) {
                    if (error is CancellationException) throw error
                    message = socialErrorMessage(error)
                } finally { toggling = false }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("댓글 ${comments?.size ?: 0}", style = HType.plex(15, FontWeight.Bold), color = HColors.ink)
            when {
                loadFailed -> Text("댓글을 불러오지 못했어요. 눌러서 다시 시도", style = HType.caption13,
                    color = HColors.textSecondary, modifier = Modifier.clickable { reload++ })
                comments == null -> Unit
                comments!!.isEmpty() -> Text("첫 댓글을 남겨 보세요", style = HType.caption13,
                    color = HColors.textSecondary)
                else -> comments!!.forEach { comment ->
                    CommentRow(comment, onDelete = {
                        scope.launch {
                            try { repository.deleteComment(comment.id); comments = comments?.filterNot { it.id == comment.id }; message = null }
                            catch (error: Exception) {
                                if (error is CancellationException) throw error
                                message = socialErrorMessage(error)
                            }
                        }
                    })
                }
            }
        }
        CommentComposer(members.filterNot { it.isMe }, onSend = { content, mentions, image ->
            try {
                val created = repository.addComment(submissionID, content, mentions, image?.first,
                    image?.second ?: "jpg")
                comments = (comments ?: emptyList()) + created
                message = null
                true
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                message = socialErrorMessage(error)
                false
            }
        })
        message?.let { Text(it, style = HType.caption13, color = HColors.danger) }
    }
}

@Composable
private fun StickerBar(reactions: List<Reaction>, names: Map<Long, String>, onToggle: (Sticker) -> Unit) {
    val byType = reactions.associateBy { it.sticker }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sticker.entries.forEach { sticker ->
                val reaction = byType[sticker]
                val mine = reaction?.reactedByMe == true
                val shape = RoundedCornerShape(20.dp)
                Row(Modifier.height(40.dp).clip(shape)
                    .background(if (mine) HColors.accentSubtle else HColors.bg)
                    .border(1.dp, if (mine) HColors.accent else HColors.border, shape)
                    .clickable { onToggle(sticker) }.padding(horizontal = 14.dp)
                    .semantics { contentDescription = "${sticker.label} ${if (mine) "취소" else "남기기"}, ${reaction?.count ?: 0}명" },
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(sticker.emoji, style = HType.plex(17))
                    Text((reaction?.count ?: 0).toString(),
                        style = HType.plex(14).copy(fontFeatureSettings = "tnum"), color = HColors.ink)
                }
            }
        }
        reactions.forEach { reaction ->
            val who = (reaction.userIds.map { names[it] ?: "나간 멤버" } +
                if (reaction.aiReacted) listOf("별이") else emptyList()).joinToString(", ")
            Text("${reaction.sticker.emoji} $who", style = HType.caption13, color = HColors.textSecondary)
        }
    }
}

@Composable
private fun CommentRow(comment: SubmissionComment, onDelete: () -> Unit) {
    var showingDetail by remember(comment.id) { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (comment.isAi) MascotAvatar(30.dp) else HAvatar(comment.author.avatar, size = 30.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(comment.author.nickname, style = HType.plex(14, FontWeight.SemiBold), color = HColors.ink)
                if (comment.isAi) Text("AI", style = HType.caption12, color = HColors.accentPressed)
                Text(koreanTimeText(comment.createdAt), style = HType.caption12, color = HColors.textSecondary)
                Spacer(Modifier.weight(1f))
                if (comment.isMine && !comment.isAi) Text("삭제", style = HType.caption12, color = HColors.textSecondary,
                    modifier = Modifier.clickable(onClick = onDelete).padding(vertical = 4.dp))
            }
            if (comment.content.isNotEmpty()) Text(comment.content, style = HType.plex(15, lineHeightMultiple = 1.5f),
                color = HColors.ink, modifier = if (comment.aiDetail != null) Modifier.clickable { showingDetail = true } else Modifier)
            comment.aiDetail?.let { detail ->
                Text(if (detail is AiDetail.PhotoScore) "눌러서 점수 보기" else "눌러서 별이의 추측 보기",
                    style = HType.caption12, color = HColors.accentPressed,
                    modifier = Modifier.clickable { showingDetail = true })
            }
            comment.imageURL?.let {
                AsyncImage(model = it.toString(), contentDescription = "${comment.author.nickname}의 댓글 사진",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(HRadius.card)),
                    contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
            }
        }
    }
    if (showingDetail) comment.aiDetail?.let { MascotDetailDialog(it) { showingDetail = false } }
}

@Composable
private fun CommentComposer(mentionable: List<GroupMember>,
    onSend: suspend (String, List<Long>, Pair<ByteArray, String>?) -> Boolean) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var image by remember { mutableStateOf<Pair<ByteArray, String>?>(null) }
    var sending by remember { mutableStateOf(false) }
    var imageError by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    val png = context.contentResolver.getType(uri) == "image/png"
                    bytes?.let { it to if (png) "png" else "jpg" }
                }.getOrNull()
            }
            when {
                loaded == null -> imageError = "사진을 읽을 수 없어요"
                loaded.first.size > MAX_IMAGE_BYTES -> imageError = "사진이 너무 커요 (10MB 이하)"
                else -> { image = loaded; imageError = null }
            }
        }
    }
    val canSend = !sending && (text.isNotBlank() || image != null)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (mentionable.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("멘션", style = HType.caption12, color = HColors.textSecondary)
            mentionable.forEach { member ->
                val nickname = member.displayed.nickname
                Text("@$nickname", style = HType.caption13, color = HColors.ink,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(HColors.surface)
                        .clickable {
                            if (!text.contains("@$nickname")) {
                                text = (if (text.isEmpty() || text.endsWith(" ")) text else "$text ") + "@$nickname "
                            }
                            picked = picked + (member.id to nickname)
                        }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        image?.let { (bytes, _) ->
            Box {
                AsyncImage(model = bytes, contentDescription = "첨부한 사진",
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(HRadius.card)), contentScale = ContentScale.Crop)
                Box(Modifier.align(Alignment.TopEnd).size(24.dp).clip(RoundedCornerShape(12.dp))
                    .background(HColors.ink).clickable { image = null }
                    .semantics { contentDescription = "사진 빼기" }, contentAlignment = Alignment.Center) {
                    HIconView(HIcon.CLOSE, size = 12.dp, color = HColors.onAccent)
                }
            }
        }
        imageError?.let { Text(it, style = HType.caption13, color = HColors.danger) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(HColors.surface)
                .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                .semantics { contentDescription = "사진 첨부" }, contentAlignment = Alignment.Center) {
                HIconView(HIcon.IMAGE, size = 20.dp)
            }
            HTextField(text, { text = it }, "댓글을 입력해 주세요", Modifier.weight(1f),
                maxLength = MAX_COMMENT_LENGTH)
            Box(Modifier.height(44.dp).clip(RoundedCornerShape(12.dp))
                .background(if (canSend) HColors.accent else HColors.disabled)
                .clickable(enabled = canSend) {
                    sending = true
                    scope.launch {
                        val ok = onSend(text.trim(), mentionedIdsIn(text, picked), image)
                        sending = false
                        if (ok) { text = ""; picked = emptyMap(); image = null; imageError = null }
                    }
                }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text("등록", style = HType.plex(14, FontWeight.SemiBold),
                    color = if (canSend) HColors.onAccent else HColors.textDisabled)
            }
        }
    }
}
