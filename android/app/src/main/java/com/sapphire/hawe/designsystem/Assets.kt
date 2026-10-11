package com.sapphire.hawe.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.R

/** Figma `icon/…` (res/drawable/ic_*, iOS 에셋에서 변환). 색을 입혀 써요. */
enum class HIcon(@DrawableRes val res: Int) {
    Calendar(R.drawable.ic_calendar),
    Camera(R.drawable.ic_camera),
    Check(R.drawable.ic_check),
    CheckSmall(R.drawable.ic_check_small),
    ChevronLeft(R.drawable.ic_chevron_left),
    ChevronRight(R.drawable.ic_chevron_right),
    Close(R.drawable.ic_close),
    Comment(R.drawable.ic_comment),
    Copy(R.drawable.ic_copy),
    Flash(R.drawable.ic_flash),
    Flip(R.drawable.ic_flip),
    Heart(R.drawable.ic_heart),
    HeartFill(R.drawable.ic_heart_fill),
    Image(R.drawable.ic_image),
    Lock(R.drawable.ic_lock),
    Mic(R.drawable.ic_mic),
    More(R.drawable.ic_more),
    Pause(R.drawable.ic_pause),
    Pencil(R.drawable.ic_pencil),
    Person(R.drawable.ic_person),
    Play(R.drawable.ic_play),
    Plus(R.drawable.ic_plus),
    Redo(R.drawable.ic_redo),
    Text(R.drawable.ic_text),
    Undo(R.drawable.ic_undo),
}

/** 아이콘을 Figma 크기 그대로 그려요. 기본 색은 ink. */
@Composable
fun HIconView(icon: HIcon, size: Dp = 24.dp, tint: Color = HColors.ink, modifier: Modifier = Modifier) {
    Icon(painterResource(icon.res), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** 원래 색 그대로 그리는 그림 (캐릭터·로고·간편 로그인 아이콘) */
@Composable
fun HImage(@DrawableRes res: Int, size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(res), contentDescription = null, modifier = modifier.size(size))
}
