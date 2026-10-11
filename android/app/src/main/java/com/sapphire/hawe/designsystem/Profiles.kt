package com.sapphire.hawe.designsystem

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.sapphire.hawe.R
import com.sapphire.hawe.model.AvatarContent
import com.sapphire.hawe.model.ProfileCharacter
import com.sapphire.hawe.model.Relationship
import kotlin.math.roundToInt

// MARK: - HAvatar

/**
 * Figma `Avatar` — 원형 surface 배경 + 캐릭터(또는 사진). 크기 24–44.
 * 멤버 줄처럼 겹쳐 놓을 때는 [ringed] 로 흰 테두리 2 를 둘러요.
 * 캐릭터 크기: 34~38 아바타 안의 캐릭터 30 → 약 0.85배, 큰 미리보기(80·88)는 0.8배.
 */
@Composable
fun HAvatar(content: AvatarContent, size: Dp = 36.dp, modifier: Modifier = Modifier, ringed: Boolean = false, characterScale: Float = 0.85f) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(HColors.surface)
            .then(if (ringed) Modifier.border(2.dp, HColors.bg, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when (content) {
            is AvatarContent.Character -> Image(
                painterResource(content.character.drawable),
                contentDescription = null,
                modifier = Modifier.size(size * characterScale),
            )
            is AvatarContent.Photo -> AsyncImage(content.url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            is AvatarContent.Local -> AsyncImage(content.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

// MARK: - 관계 장식 · 멤버 줄

/**
 * 멤버 캐릭터 사이에 들어가는 작은 원형(흰 바탕) 장식.
 * Figma `Decor/…` 기준: 원 지름 = 아바타 × 0.59, 안쪽 그림 = 원 × 0.7
 * 가족·그저그런사이는 Figma 에 없어서 기본 아이콘(집)·점 세 개로 대신해요 (사용자 확인).
 */
@Composable
fun RelationshipDecoration(relationship: Relationship, diameter: Dp = 20.dp, modifier: Modifier = Modifier) {
    val glyph = diameter * 0.7f
    Box(modifier.size(diameter).background(HColors.bg, CircleShape), contentAlignment = Alignment.Center) {
        when (relationship) {
            Relationship.Lover -> HImage(R.drawable.decor_lover, glyph)
            Relationship.Friend -> HImage(R.drawable.decor_friend, glyph)
            Relationship.Rival -> HImage(R.drawable.decor_enemy, glyph)
            Relationship.Family -> Icon(Icons.Filled.Home, null, tint = relationship.decorColor, modifier = Modifier.size(glyph))
            Relationship.Soso -> Canvas(Modifier.size(glyph)) {
                val r = size.width * 0.11f
                for (i in -1..1) drawCircle(relationship.decorColor, r, Offset(center.x + i * size.width * 0.32f, center.y))
            }
        }
    }
}

/** 아바타 사이사이에 관계 장식이 들어가는 줄 — 아바타 34(흰 테두리 2) · 장식 20 · 서로 5 겹침 */
@Composable
fun MemberRow(members: List<AvatarContent>, relationship: Relationship, modifier: Modifier = Modifier, avatarSize: Dp = 34.dp) {
    val decor = (avatarSize.value * 0.59f).roundToInt().dp
    Row(modifier, horizontalArrangement = Arrangement.spacedBy((-5).dp), verticalAlignment = Alignment.CenterVertically) {
        members.forEachIndexed { index, member ->
            if (index > 0) RelationshipDecoration(relationship, decor, Modifier.zIndex(1f))
            HAvatar(member, avatarSize, ringed = true)
        }
    }
}

// MARK: - 로고

/** '오늘의 타일' 심볼 — 60 이하에서는 얼굴 없는 버전 (CLAUDE.md §3-8) */
@Composable
fun HLogoSymbol(size: Dp = 30.dp, modifier: Modifier = Modifier) {
    HImage(if (size > 60.dp) R.drawable.logo_symbol else R.drawable.logo_symbol_small, size, modifier)
}

/** 워드마크 "HA'WE" — Fredoka SemiBold, 아포스트로피만 accent */
@Composable
fun HWordmark(size: Int = 24, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append("HA")
            withStyle(SpanStyle(color = HColors.accent)) { append("'") }
            append("WE")
        },
        style = TextStyle(fontFamily = WordmarkFamily, fontWeight = FontWeight.SemiBold, fontSize = size.sp),
        color = HColors.ink,
        modifier = modifier,
    )
}

/** 심볼 + 워드마크 (Figma `logo/lockup`: 심볼 30, 워드마크 24, 간격 8) */
@Composable
fun HLogoLockup(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        HLogoSymbol(30.dp)
        HWordmark(24)
    }
}

// MARK: - 캐릭터 고르기

/**
 * 캐릭터 7종 + 내 사진 고르기 (A3 회원가입 · 프로필, 16-1 그룹 내 프로필)
 * 4열. 고른 칸은 흰 바탕 + ink 테두리 2.5, 이름 12 Bold / 나머지는 surface + 12 Medium textSecondary.
 * '내 사진'은 점선 칸이고, 누르면 사진 선택기가 열려요.
 */
@Composable
fun CharacterPicker(
    selection: AvatarContent,
    onSelect: (AvatarContent) -> Unit,
    modifier: Modifier = Modifier,
    cellSize: Dp = 66.dp,
    characterSize: Dp = 50.dp,
    rowSpacing: Dp = 12.dp,
) {
    var lastPhoto by remember { mutableStateOf(selection.takeIf { it.isPhoto }) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val photo = AvatarContent.Local(uri)
            lastPhoto = photo
            onSelect(photo)
        }
    }
    val cells: List<AvatarContent?> = ProfileCharacter.entries.map { AvatarContent.Character(it) } + listOf(null)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
        cells.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { cell ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (cell is AvatarContent.Character) {
                            val selected = selection == cell
                            PickerCell(cell.character.displayName, selected, false, cellSize, onClick = { onSelect(cell) }) {
                                HImage(cell.character.drawable, characterSize)
                            }
                        } else {
                            val photo = if (selection.isPhoto) selection else lastPhoto
                            PickerCell("내 사진", selection.isPhoto, photo == null, cellSize, onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) {
                                if (photo != null) HAvatar(photo, cellSize) else HIconView(HIcon.Image, 22.dp)
                            }
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PickerCell(name: String, selected: Boolean, dashed: Boolean, size: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.plainClickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (selected) HColors.bg else HColors.surface),
            contentAlignment = Alignment.Center,
        ) {
            content()
            if (selected) {
                Box(Modifier.fillMaxSize().border(2.5.dp, HColors.ink, CircleShape))
            } else if (dashed) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 2.dp.toPx()
                    drawCircle(
                        HColors.controlBorder,
                        radius = (this.size.minDimension - stroke) / 2,
                        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                    )
                }
            }
        }
        Text(
            name,
            style = plex(12, if (selected) FontWeight.Bold else FontWeight.Medium),
            color = if (selected) HColors.ink else HColors.textSecondary,
        )
    }
}

/** 원 미리보기 + 설명 + 이름 (A3 · 16-1 · 프로필 편집 위쪽) */
@Composable
fun ProfilePreviewRow(avatar: AvatarContent, caption: String, name: String, size: Dp = 88.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        HAvatar(avatar, size, characterScale = 0.8f)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(caption, style = HType.caption13, color = HColors.textSecondary)
            Text(name.ifEmpty { " " }, style = plex(20, FontWeight.Bold), color = HColors.ink, maxLines = 1)
        }
    }
}
