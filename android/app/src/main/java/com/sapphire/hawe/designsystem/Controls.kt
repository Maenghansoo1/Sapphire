package com.sapphire.hawe.designsystem

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 눌림 표시(물결) 없이 누를 수 있게 — 디자인이 눌림 색을 따로 정해요. */
@Composable
fun Modifier.plainClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick)

// MARK: - HButton

/** Figma `Button` — 하단 고정 CTA 는 primary 만, 보조 행동은 secondary/gray, 탈퇴·로그아웃 확정은 danger. */
enum class HButtonKind { Primary, Secondary, Gray, Danger }

/** 높이 56(기본) · 모서리 16 · 좌우 20 · 17 SemiBold. 꺼져 있으면 Disabled 모양. */
@Composable
fun HButton(
    title: String,
    modifier: Modifier = Modifier,
    kind: HButtonKind = HButtonKind.Primary,
    height: Dp = HLayout.ctaHeight,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(HRadius.button)
    val background = when {
        !enabled -> HColors.disabled
        kind == HButtonKind.Primary -> if (pressed) HColors.accentPressed else HColors.accent
        kind == HButtonKind.Secondary -> if (pressed) HColors.surfaceStrong else HColors.bg
        kind == HButtonKind.Gray -> if (pressed) HColors.disabled else HColors.surfaceStrong
        else -> if (pressed) HColors.danger.copy(alpha = 0.85f) else HColors.danger
    }
    val foreground = when {
        !enabled -> HColors.textSecondary
        kind == HButtonKind.Primary || kind == HButtonKind.Danger -> HColors.onAccent
        else -> HColors.ink
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(background, shape)
            .then(if (enabled && kind == HButtonKind.Secondary) Modifier.border(1.dp, HColors.border, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = plex(17, FontWeight.SemiBold), color = foreground, maxLines = 1)
    }
}

// MARK: - HChip

/** Figma `Chip` — 높이 44 · 알약형 · 좌우 18 · 15pt · 최소 너비 80(글자에 맞춰 늘어남) · 가운데 정렬. 선택은 검정. */
@Composable
fun HChip(title: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 80.dp)
            .height(44.dp)
            .background(if (selected) HColors.ink else HColors.bg, CircleShape)
            .border(1.dp, if (selected) HColors.ink else HColors.border, CircleShape)
            .plainClickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            style = plex(15, if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) HColors.onAccent else HColors.ink,
            maxLines = 1,
        )
    }
}

// MARK: - HNavBar

sealed interface NavLeading {
    data object None : NavLeading
    data class Back(val onClick: () -> Unit) : NavLeading
    data class Close(val onClick: () -> Unit) : NavLeading
}

/** Figma `Header` — 왼쪽 44(뒤로/닫기) + 가운데 제목(17 Bold) + 오른쪽 영역. 높이 52. */
@Composable
fun HNavBar(
    title: String? = null,
    modifier: Modifier = Modifier,
    titleStyle: androidx.compose.ui.text.TextStyle = HType.navTitle,
    leading: NavLeading = NavLeading.None,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Box(modifier.fillMaxWidth().height(HLayout.headerHeight), contentAlignment = Alignment.Center) {
        if (title != null) {
            Text(
                title,
                style = titleStyle,
                color = HColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = HLayout.minTouch + 8.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            when (leading) {
                NavLeading.None -> Spacer(Modifier.size(HLayout.minTouch))
                is NavLeading.Back -> NavIconButton(HIcon.ChevronLeft, 24.dp, leading.onClick)
                is NavLeading.Close -> NavIconButton(HIcon.Close, 22.dp, leading.onClick)
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.heightIn(min = HLayout.minTouch),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                content = trailing,
            )
        }
    }
}

@Composable
private fun NavIconButton(icon: HIcon, size: Dp, onClick: () -> Unit) {
    Box(Modifier.size(HLayout.minTouch).plainClickable(onClick = onClick), contentAlignment = Alignment.CenterStart) {
        HIconView(icon, size)
    }
}

/** 헤더 오른쪽 단계 표시 (예: "1 / 2") */
@Composable
fun HNavStep(text: String) {
    Text(text, style = plex(14, FontWeight.SemiBold), color = HColors.textSecondary)
}

// MARK: - HTextField

/**
 * Figma `Input` — 높이 54 · 모서리 14 · 테두리 border · 좌우 16 · 16pt (placeholder textTertiary)
 * 위에 라벨(14 SemiBold), 아래에 안내 문구·글자 수(13). [error] 가 있으면 테두리·문구가 danger.
 */
@Composable
fun HTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    isSecure: Boolean = false,
    helper: String? = null,
    maxLength: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    error: String? = null,
    textSize: Int = 16,
    labelSpacing: Dp = 8.dp,
) {
    val shape = RoundedCornerShape(HRadius.input)
    val noAutoFix = keyboardType == KeyboardType.Email || isSecure
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(labelSpacing)) {
        if (label != null) Text(label, style = HType.fieldLabel, color = HColors.textSecondary)
        BasicTextField(
            value = value,
            onValueChange = { new -> onValueChange(if (maxLength != null) new.take(maxLength) else new) },
            singleLine = true,
            textStyle = plex(textSize).copy(color = HColors.ink),
            cursorBrush = SolidColor(HColors.ink),
            visualTransformation = if (isSecure) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isSecure) KeyboardType.Password else keyboardType,
                capitalization = if (noAutoFix) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
                autoCorrectEnabled = !noAutoFix,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(HColors.bg, shape)
                .border(1.dp, if (error != null) HColors.danger else HColors.border, shape)
                .padding(horizontal = 16.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = plex(textSize), color = HColors.textTertiary, maxLines = 1)
                    inner()
                }
            },
        )
        if (error != null) {
            Text(error, style = HType.caption13, color = HColors.danger)
        } else if (helper != null || maxLength != null) {
            Row(Modifier.fillMaxWidth()) {
                if (helper != null) Text(helper, style = HType.caption13, color = HColors.textSecondary)
                Spacer(Modifier.weight(1f).width(8.dp))
                if (maxLength != null) Text("${value.length} / $maxLength", style = HType.caption13, color = HColors.textSecondary)
            }
        }
    }
}

// MARK: - HToggle

/** Figma `Toggle` — 52×32 알약, 켜짐 ink / 꺼짐 controlOff, 흰 손잡이 26. 꺼져 있으면 흐려져요. */
@Composable
fun HToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val knob by animateDpAsState(if (checked) 23.dp else 3.dp, label = "knob")
    Box(
        modifier
            .size(52.dp, 32.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .background(if (checked) HColors.ink else HColors.controlOff, CircleShape)
            .plainClickable(enabled = enabled) { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .offset(x = knob, y = 3.dp)
                .size(26.dp)
                .background(Color.White, CircleShape),
        )
    }
}

// MARK: - HCheckbox

/** 체크박스 모양만 — 22 · 모서리 6 · 테두리 1.5. On: ink + 흰 체크(14) */
@Composable
fun HCheckboxMark(checked: Boolean) {
    val shape = RoundedCornerShape(HRadius.checkbox)
    Box(
        Modifier
            .size(22.dp)
            .background(if (checked) HColors.ink else HColors.bg, shape)
            .border(1.5.dp, if (checked) HColors.ink else HColors.controlBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) HIconView(HIcon.CheckSmall, 14.dp, HColors.onAccent)
    }
}

/** 라벨까지 한 줄 전체(높이 44)가 눌려요. */
@Composable
fun HCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, label: @Composable RowScope.() -> Unit) {
    Row(
        modifier.heightIn(min = HLayout.minTouch).plainClickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HCheckboxMark(checked)
        label()
    }
}

// MARK: - HListRow

/** Figma `ListRow` — 라벨(15) + 값(14 textSecondary) + 꺾쇠(16), 아래 구분선. 기본 높이 52. */
@Composable
fun HListRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    height: Dp = 52.dp,
    showsDivider: Boolean = true,
    showsChevron: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(modifier.fillMaxWidth().height(height).plainClickable(enabled = enabled, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = HType.body15, color = HColors.ink, maxLines = 1)
            Spacer(Modifier.weight(1f).width(12.dp))
            if (value != null) {
                Text(value, style = plex(14), color = HColors.textSecondary, maxLines = 1)
                Spacer(Modifier.width(6.dp))
            }
            if (showsChevron) HIconView(HIcon.ChevronRight, 16.dp, HColors.textTertiary)
        }
        if (showsDivider) HDivider(Modifier.align(Alignment.BottomCenter))
    }
}

/** 1pt 구분선 (line) */
@Composable
fun HDivider(modifier: Modifier = Modifier, color: Color = HColors.line) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** 테두리 있는 알약 버튼 (예: '내 프로필 편집', '콕 찌르기', '연결하기') — 높이 34 · 좌우 14 · 13 SemiBold */
@Composable
fun HPillButton(
    title: String,
    modifier: Modifier = Modifier,
    height: Dp = 34.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(height)
            .border(1.dp, if (enabled) HColors.border else HColors.line, CircleShape)
            .plainClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = plex(13, FontWeight.SemiBold), color = if (enabled) HColors.ink else HColors.textTertiary)
    }
}

/** 둥근 배경 + 테두리 한 번에 */
fun Modifier.card(background: Color = Color.Transparent, border: Color? = null, shape: Shape): Modifier =
    this.background(background, shape).then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
