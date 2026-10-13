package com.sapphire.hawe.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.brand.HLogoLockup
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.layout.HCtaScaffold
import com.sapphire.hawe.designsystem.tokens.*

@Composable
fun StartScreen(showsBack: Boolean = false, onBack: () -> Unit = {},
    onCreateGroup: () -> Unit, onJoinGroup: () -> Unit, onMyPage: () -> Unit) {
    Box(Modifier.fillMaxSize().background(HColors.bg)) {
        Column {
            if (showsBack) Box(Modifier.padding(horizontal = HLayout.screenPadding)) {
                HNavBar(leading = HNavLeading.BACK, onLeading = onBack)
            }
            HCtaScaffold(content = {
                Column(Modifier.padding(top = if (showsBack) 0.dp else HLayout.introTopInset),
                    verticalArrangement = Arrangement.spacedBy(48.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HLogoLockup()
                        Text("매일 하나의 미션,\n우리만의 기록",
                            style = HType.plex(30, FontWeight.Bold, 1.3f), color = HColors.ink)
                        Text("오늘은 그림일까, 사진일까?\n매일 랜덤으로 정해지는 미션을 최대 6명이 함께해요.",
                            style = HType.plex(15, lineHeightMultiple = 1.6f), color = HColors.textSecondary)
                    }
                    MissionRow(MissionType.DRAW, MissionType.PHOTO, MissionType.WRITE)
                }
            }, cta = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HButton("새 그룹 만들기") { onCreateGroup() }
                    HButton("초대 코드로 참여하기", kind = HButtonKind.SECONDARY) { onJoinGroup() }
                }
            })
        }
        Box(Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 14.dp)
            .size(HLayout.minTouch).clickable(onClick = onMyPage)
            .semantics { contentDescription = "마이페이지" },
            contentAlignment = Alignment.Center) { HIconView(HIcon.PERSON, size = 22.dp) }
    }
}

@Composable
private fun MissionRow(vararg types: MissionType) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        types.forEach { MissionCard(it, Modifier.weight(1f)) }
    }
}

@Composable
private fun MissionCard(type: MissionType, modifier: Modifier = Modifier) {
    Column(modifier.background(HColors.surface, RoundedCornerShape(HRadius.card)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)) {
        HIconView(type.icon, size = 26.dp)
        Text(type.displayName, style = HType.plex(16, FontWeight.SemiBold), color = HColors.ink)
    }
}
