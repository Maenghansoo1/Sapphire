package com.sapphire.hawe.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.HButton
import com.sapphire.hawe.designsystem.HButtonKind
import com.sapphire.hawe.designsystem.HCTAScaffold
import com.sapphire.hawe.designsystem.HColors
import com.sapphire.hawe.designsystem.HIcon
import com.sapphire.hawe.designsystem.HIconView
import com.sapphire.hawe.designsystem.HLayout
import com.sapphire.hawe.designsystem.HLogoLockup
import com.sapphire.hawe.designsystem.HNavBar
import com.sapphire.hawe.designsystem.HRadius
import com.sapphire.hawe.designsystem.HScreen
import com.sapphire.hawe.designsystem.HType
import com.sapphire.hawe.designsystem.NavLeading
import com.sapphire.hawe.designsystem.plainClickable
import com.sapphire.hawe.designsystem.plex
import com.sapphire.hawe.model.MissionType

/**
 * 01 시작 (Figma 1:314) — 참여한 그룹이 하나도 없을 때, 또는 04 홈의 + 로 열었을 때([onBack] 이 있으면 뒤로 가기)
 * TODO: 2단계에서 02 그룹 만들기 / 03 초대 코드 / 17 마이페이지 연결
 */
@Composable
fun StartScreen(
    onBack: (() -> Unit)? = null,
    onCreateGroup: () -> Unit = {},
    onJoinGroup: () -> Unit = {},
    onMyPage: () -> Unit = {},
) {
    HScreen {
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize()) {
                if (onBack != null) HNavBar(modifier = Modifier.padding(horizontal = HLayout.screenPadding), leading = NavLeading.Back(onBack))
                HCTAScaffold(
                    content = {
                        Column(
                            Modifier.padding(top = if (onBack != null) 0.dp else HLayout.introTopInset),
                            verticalArrangement = Arrangement.spacedBy(48.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                HLogoLockup()
                                Text("매일 하나의 미션,\n우리만의 기록", style = plex(30, FontWeight.Bold, lineHeight = 1.3f), color = HColors.ink)
                                Text(
                                    "오늘은 그림일까, 사진일까?\n매일 랜덤으로 정해지는 미션을 최대 6명이 함께해요.",
                                    style = plex(15, lineHeight = 1.6f),
                                    color = HColors.textSecondary,
                                )
                            }
                            // 2열 · 간격 12 · 카드 surface 모서리 20 · 안쪽 20 · 아이콘 26 → 글자 사이 28
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                MissionType.entries.chunked(2).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        row.forEach { type ->
                                            Column(
                                                Modifier
                                                    .weight(1f)
                                                    .background(HColors.surface, RoundedCornerShape(HRadius.card))
                                                    .padding(20.dp),
                                                verticalArrangement = Arrangement.spacedBy(28.dp),
                                            ) {
                                                HIconView(type.icon, 26.dp)
                                                Text(type.displayName, style = plex(16, FontWeight.SemiBold), color = HColors.ink)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    cta = {
                        HButton("새 그룹 만들기", onClick = onCreateGroup)
                        HButton("초대 코드로 참여하기", kind = HButtonKind.Secondary, onClick = onJoinGroup)
                    },
                )
            }
            // 오른쪽 위 마이페이지 (44 · 아이콘 22)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 14.dp)
                    .size(HLayout.minTouch)
                    .plainClickable(onClick = onMyPage),
                contentAlignment = Alignment.Center,
            ) {
                HIconView(HIcon.Person, 22.dp)
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun StartPreview() {
    StartScreen()
}
