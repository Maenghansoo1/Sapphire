package com.sapphire.hawe.designsystem.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.tokens.*

data class HFabMenuItem(val title: String, val icon: HIcon, val onClick: () -> Unit)

private val shadowColor = Color(0xFF121A26)

/**
 * 오른쪽 아래에 떠 있는 동그란 메뉴 버튼 (Figma 04 홈 · 메뉴 버튼 / 메뉴 열림)
 * 누르면 화면이 어두워지고 버튼 위로 메뉴가 떠요. 버튼은 + → × 로 바뀌어요.
 * 화면 전체를 덮는 Box 안에 마지막 자식으로 넣어요.
 * FAB 지름 56 · 메뉴 동그라미 48 · 메뉴 사이 16 · 라벨과 동그라미 사이 12
 */
@Composable
fun HFabMenu(open: Boolean, onOpenChange: (Boolean) -> Unit, items: List<HFabMenuItem>,
    bottomPadding: Dp = 8.dp) {
    BackHandler(enabled = open) { onOpenChange(false) }
    val rotation by animateFloatAsState(if (open) 45f else 0f, label = "fabRotation")
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(HColors.ink.copy(alpha = 0.4f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    onOpenChange(false)
                })
        }
        Column(Modifier.align(Alignment.BottomEnd)
                .padding(end = HLayout.screenPaddingCompact, bottom = bottomPadding),
            horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AnimatedVisibility(open, enter = fadeIn() + slideInVertically { it / 6 },
                exit = fadeOut() + slideOutVertically { it / 6 }) {
                Column(Modifier.padding(end = 4.dp), horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items.forEach { item -> MenuRow(item) { onOpenChange(false); item.onClick() } }
                }
            }
            Box(Modifier.size(56.dp)
                    .shadow(10.dp, CircleShape, ambientColor = shadowColor, spotColor = shadowColor)
                    .background(HColors.accent, CircleShape)
                    .clickable { onOpenChange(!open) }
                    .semantics { contentDescription = if (open) "메뉴 닫기" else "메뉴 열기" },
                contentAlignment = Alignment.Center) {
                HIconView(HIcon.PLUS, Modifier.rotate(rotation), size = 24.dp, color = HColors.onAccent)
            }
        }
    }
}

@Composable
private fun MenuRow(item: HFabMenuItem, onClick: () -> Unit) {
    Row(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
            onClick = onClick).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(item.title,
            Modifier.shadow(6.dp, CircleShape, ambientColor = shadowColor, spotColor = shadowColor)
                .background(HColors.bg, CircleShape).padding(horizontal = 14.dp, vertical = 9.dp),
            style = HType.plex(15, FontWeight.SemiBold), color = HColors.ink)
        Box(Modifier.size(48.dp)
                .shadow(6.dp, CircleShape, ambientColor = shadowColor, spotColor = shadowColor)
                .background(HColors.bg, CircleShape),
            contentAlignment = Alignment.Center) { HIconView(item.icon, size = 22.dp) }
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun HFabMenuPreview() {
    var open by remember { mutableStateOf(true) }
    HFabMenu(open, { open = it }, listOf(
        HFabMenuItem("새 그룹 만들기", HIcon.PLUS) {},
        HFabMenuItem("초대 코드로 참여하기", HIcon.ENTER) {},
        HFabMenuItem("마이페이지", HIcon.PERSON) {},
    ))
}
