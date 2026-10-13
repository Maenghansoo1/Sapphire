package com.sapphire.hawe.designsystem.assets

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.R
import com.sapphire.hawe.designsystem.tokens.HColors

enum class HIcon(@DrawableRes val drawable: Int) {
    CALENDAR(R.drawable.ic_calendar), CAMERA(R.drawable.ic_camera), CHECK(R.drawable.ic_check),
    CHECK_SMALL(R.drawable.ic_check_small), CHEVRON_LEFT(R.drawable.ic_chevron_left),
    CHEVRON_RIGHT(R.drawable.ic_chevron_right), CLOSE(R.drawable.ic_close), COMMENT(R.drawable.ic_comment),
    COPY(R.drawable.ic_copy), FLASH(R.drawable.ic_flash), FLIP(R.drawable.ic_flip),
    HEART(R.drawable.ic_heart), HEART_FILL(R.drawable.ic_heart_fill), IMAGE(R.drawable.ic_image),
    LOCK(R.drawable.ic_lock), MORE(R.drawable.ic_more),
    PENCIL(R.drawable.ic_pencil), PERSON(R.drawable.ic_person),
    PLUS(R.drawable.ic_plus), REDO(R.drawable.ic_redo), TEXT(R.drawable.ic_text), UNDO(R.drawable.ic_undo),
}

@Composable
fun HIconView(icon: HIcon, modifier: Modifier = Modifier, size: Dp = 24.dp, color: Color = HColors.ink) {
    Icon(painterResource(icon.drawable), contentDescription = null, modifier = modifier.size(size), tint = color)
}
