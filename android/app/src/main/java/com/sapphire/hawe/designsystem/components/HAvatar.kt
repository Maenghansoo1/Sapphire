package com.sapphire.hawe.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sapphire.hawe.core.model.AvatarContent
import com.sapphire.hawe.designsystem.tokens.HColors

@Composable
fun HAvatar(content: AvatarContent, modifier: Modifier = Modifier, size: Dp = 36.dp,
            ringed: Boolean = false, characterScale: Float = 0.85f) {
    Box(modifier.size(size).clip(CircleShape).background(HColors.surface)
        .then(if (ringed) Modifier.border(2.dp, HColors.bg, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
        when (content) {
            is AvatarContent.Character -> Image(painterResource(content.character.drawable), null,
                Modifier.size(size * characterScale), contentScale = ContentScale.Fit)
            is AvatarContent.Photo -> AsyncImage(content.uri, null, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
        }
    }
}
