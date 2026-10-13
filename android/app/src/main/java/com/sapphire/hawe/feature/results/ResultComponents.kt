package com.sapphire.hawe.feature.results

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sapphire.hawe.core.model.MissionSpec
import com.sapphire.hawe.core.model.MissionTemplates
import com.sapphire.hawe.core.model.Profile
import com.sapphire.hawe.core.model.Submission
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.components.HAvatar
import com.sapphire.hawe.designsystem.components.HSliderEndLabels
import com.sapphire.hawe.designsystem.components.HSliderMark
import com.sapphire.hawe.designsystem.components.HSliderTrack
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HRadius
import com.sapphire.hawe.designsystem.tokens.HType

private fun imageModel(submission: Submission): Any? = localImageFile(submission) ?:
    submission.mediaURL?.takeIf { it.scheme != "file" }?.toString()

@Composable
fun PhotoArtwork(submission: Submission, height: Dp, modifier: Modifier = Modifier) {
    val model = imageModel(submission)
    var loaded by remember(model) { mutableStateOf(false) }
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(HRadius.card))
        .background(HColors.surfaceStrong), contentAlignment = Alignment.Center) {
        if (!loaded) Text("[${submission.author.nickname}의 사진]", style = HType.plex(14), color = HColors.textSecondary)
        if (model != null) AsyncImage(model = model, contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            onSuccess = { loaded = true }, onError = { loaded = false })
    }
}

@Composable
fun DrawingArtwork(submission: Submission, height: Dp, drawingSize: Dp,
    modifier: Modifier = Modifier, cornerRadius: Dp = HRadius.card) {
    val model = imageModel(submission)
    var loaded by remember(model) { mutableStateOf(false) }
    val shape = RoundedCornerShape(cornerRadius)
    Box(modifier.fillMaxWidth().height(height).clip(shape).background(HColors.bg)
        .border(1.dp, HColors.line, shape), contentAlignment = Alignment.Center) {
        if (!loaded) Text("[${submission.author.nickname}의 그림]", style = HType.plex(14), color = HColors.textSecondary)
        if (model != null) AsyncImage(model = model, contentDescription = null,
            modifier = Modifier.size(drawingSize), contentScale = ContentScale.Fit,
            onSuccess = { loaded = true }, onError = { loaded = false })
    }
}
@Composable
fun LikeButton(count: Int, isLiked: Boolean, onClick: () -> Unit) {
    Row(Modifier.height(40.dp).clickable(onClick = onClick)
        .semantics { contentDescription = "${if (isLiked) "좋아요 취소" else "좋아요"}, ${count}개" },
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        HIconView(if (isLiked) HIcon.HEART_FILL else HIcon.HEART, size = 20.dp)
        Text(count.toString(), style = HType.plex(14).copy(fontFeatureSettings = "tnum"), color = HColors.ink)
    }
}

@Composable
fun PendingMembers(members: List<Profile>) {
    Column {
        Text("아직 안 올렸어요", style = HType.plex(14, FontWeight.Bold), color = HColors.ink,
            modifier = Modifier.padding(bottom = 6.dp))
        members.forEach { member ->
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                HAvatar(member.avatar, size = 34.dp)
                Text(member.nickname, style = HType.plex(15, FontWeight.SemiBold), color = HColors.ink)
                Spacer(Modifier.weight(1f))
                Text("아직", style = HType.caption13, color = HColors.textTertiary)
            }
        }
    }
}

private val templateAnswerTemplates = setOf(MissionTemplates.TEXT_ONE_WORD, MissionTemplates.TEXT_REVIEW,
    MissionTemplates.TEXT_BALANCE, MissionTemplates.SLIDER_POSITION, MissionTemplates.SLIDER_TASTE,
    MissionTemplates.SLIDER_CONSENSUS)

/** 새 틀(B5)이라 글 한 덩어리가 아닌 전용 모양([TemplateAnswer])으로 그리는 제출물인지. */
fun hasTemplateAnswer(template: String) = template in templateAnswerTemplates

/**
 * 제출물 본문. 새 틀은 틀별 모양(한 단어+이유, 별점+후기, 선택+이유, 슬라이더 위치)이고,
 * 그 밖의 글 틀은 지금까지처럼 글만 보여요. [large] 는 상세 화면용 큰 글씨.
 */
@Composable
fun TemplateAnswer(submission: Submission, spec: MissionSpec, large: Boolean = false) {
    val body = HType.plex(if (large) 17 else 15, lineHeightMultiple = 1.6f)
    when (submission.template) {
        MissionTemplates.TEXT_ONE_WORD -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(submission.text.orEmpty(), style = HType.plex(if (large) 30 else 22, FontWeight.Bold), color = HColors.ink)
            submission.caption?.let { Text(it, style = body, color = HColors.textBody) }
        }
        MissionTemplates.TEXT_REVIEW -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val stars = submission.number ?: 0
            Text(starsText(stars), Modifier.clearAndSetSemantics { contentDescription = "별점 ${stars}점" },
                style = HType.plex(if (large) 24 else 18), color = HColors.accent)
            Text(submission.text.orEmpty(), style = HType.plex(if (large) 19 else 16, lineHeightMultiple = 1.65f),
                color = HColors.ink)
        }
        MissionTemplates.TEXT_BALANCE -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BalancePick(submission.choice, spec, large)
            submission.caption?.let { Text(it, style = body, color = HColors.textBody) }
        }
        MissionTemplates.SLIDER_POSITION, MissionTemplates.SLIDER_TASTE ->
            SliderAnswer(null, submission.number, spec, large)
        MissionTemplates.SLIDER_CONSENSUS -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // 두 막대는 같은 눈금이라 양 끝 라벨은 아래에 한 번만
            SliderAnswer("내 위치", submission.number, spec, large, showEndLabels = false)
            SliderAnswer("평균 예측", submission.guess, spec, large)
        }
        else -> Text(submission.text ?: "", style = HType.plex(16, lineHeightMultiple = 1.65f), color = HColors.ink)
    }
}

@Composable
private fun BalancePick(choice: String?, spec: MissionSpec, large: Boolean) {
    val option = when (choice) { "A" -> spec.optionA; "B" -> spec.optionB; else -> null }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(if (large) 32.dp else 28.dp).background(HColors.accent, CircleShape),
            contentAlignment = Alignment.Center) {
            Text(choice.orEmpty(), style = HType.plex(14, FontWeight.Bold), color = HColors.onAccent)
        }
        Text(option?.takeIf { it.isNotBlank() } ?: "${choice.orEmpty()} 선택", Modifier.weight(1f),
            style = HType.plex(if (large) 20 else 17, FontWeight.SemiBold, 1.4f), color = HColors.ink)
    }
}

/** 읽기 전용 슬라이더 한 줄(임시 UI): 라벨·값, 막대 위 점, 양 끝 라벨. */
@Composable
private fun SliderAnswer(label: String?, value: Int?, spec: MissionSpec, large: Boolean, showEndLabels: Boolean = true) {
    value ?: return
    val description = listOfNotNull(label, "$value", spec.left?.let { "왼쪽 $it" }, spec.right?.let { "오른쪽 $it" })
        .joinToString(", ")
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.orEmpty(), Modifier.weight(1f), style = HType.caption13, color = HColors.textSecondary)
            Text(value.toString(), style = HType.plex(if (large) 24 else 18, FontWeight.Bold)
                .copy(fontFeatureSettings = "tnum"), color = HColors.ink)
        }
        HSliderTrack(listOf(HSliderMark(value, HColors.accent, if (large) 18.dp else 14.dp)))
        if (showEndLabels) HSliderEndLabels(spec.left, spec.right)
    }
}
