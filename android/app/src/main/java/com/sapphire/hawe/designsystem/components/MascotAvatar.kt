package com.sapphire.hawe.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.sapphire.hawe.R
import com.sapphire.hawe.designsystem.tokens.HColors

@Composable
fun MascotAvatar(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(HColors.surface), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.mascot_byeol), "별이", Modifier.size(size), contentScale = ContentScale.Fit)
    }
}
