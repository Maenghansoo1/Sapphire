package com.sapphire.hawe.feature.results

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sapphire.hawe.core.model.Profile
import com.sapphire.hawe.core.model.Submission
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.components.HAvatar
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
fun VoiceBars(levels: List<Float>, progress: Float = 0f, barWidth: Dp = 3.dp,
    spacing: Dp = 3.dp, maxHeight: Dp = 24.dp, minHeight: Dp = 6.dp) {
    Row(Modifier.height(maxHeight), horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically) {
        levels.forEachIndexed { index, level ->
            val height = maxOf(minHeight, maxHeight * level.coerceIn(0f, 1f))
            Box(Modifier.width(barWidth).height(height).background(
                if (index.toFloat() / levels.size.coerceAtLeast(1) < progress) HColors.accent else HColors.controlBorder,
                RoundedCornerShape(barWidth / 2f)))
        }
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
