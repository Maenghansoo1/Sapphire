package com.sapphire.hawe.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
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
fun StartScreen(onCreateGroup: () -> Unit, onJoinGroup: () -> Unit, onMyPage: () -> Unit) {
    Box(Modifier.fillMaxSize().background(HColors.bg)) {
        Column {
            HCtaScaffold(content = {
                Column(Modifier.padding(top = HLayout.introTopInset),
                    verticalArrangement = Arrangement.spacedBy(48.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HLogoLockup()
                        Text("매일 하나의 미션,\n우리만의 기록",
                            style = HType.plex(30, FontWeight.Bold, 1.3f), color = HColors.ink)
                        Text("오늘은 그림일까, 사진일까?\n매일 랜덤으로 정해지는 미션을 최대 6명이 함께해요.",
                            style = HType.plex(15, lineHeightMultiple = 1.6f), color = HColors.textSecondary)
                    }
                    MissionPreview()
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

/** 예시 미션 카드 3장을 겹쳐 둔 그림이에요. 버튼으로 오해하지 않도록 기울여 겹치고, 누를 수 없어요.
 *  Figma 01 시작 · Mission preview — 카드 220×150 · 모서리 18 · 안쪽 18, 아래 안내 13 */
@Composable
private fun MissionPreview() {
    Column(Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = "그림, 사진, 글 중 하나의 미션이 매일 랜덤으로 도착해요"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
            MissionPreviewCard(MissionType.WRITE, front = false,
                modifier = Modifier.offset(x = (-55).dp, y = 10.dp).rotate(7f))
            MissionPreviewCard(MissionType.PHOTO, front = false,
                modifier = Modifier.offset(x = 53.dp, y = 4.dp).rotate(-7f))
            MissionPreviewCard(MissionType.DRAW, front = true, prompt = "30초 안에 서로의\n얼굴을 그려보세요",
                modifier = Modifier.offset(y = (-18).dp).rotate(1.5f))
        }
        Text("매일 이런 미션 중 하나가 랜덤으로 도착해요",
            style = HType.plex(13), color = HColors.textSecondary)
    }
}

/** 시작 화면의 예시 미션 카드. 앞 카드는 미션 문장까지, 뒤 카드는 아이콘만 보여요. */
@Composable
private fun MissionPreviewCard(type: MissionType, front: Boolean, modifier: Modifier = Modifier,
    prompt: String? = null) {
    val shape = RoundedCornerShape(18.dp)
    val trailing = type == MissionType.PHOTO
    var card = modifier.size(220.dp, 150.dp)
    card = if (front) card
        .shadow(14.dp, shape, ambientColor = Color(0x1A121A26), spotColor = Color(0x1A121A26))
        .background(HColors.bg, shape)
        .border(1.dp, Color.Black.copy(alpha = 0.06f), shape)
    else card.background(HColors.surface, shape)
    Column(card.padding(18.dp),
        horizontalAlignment = if (trailing) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (front) {
            Row(Modifier.background(HColors.accentSubtle, CircleShape)
                    .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HIconView(type.icon, size = 14.dp, color = HColors.accent)
                Text("오늘의 미션 · ${type.displayName}",
                    style = HType.plex(12, FontWeight.SemiBold), color = HColors.accent)
            }
            if (prompt != null) Text(prompt, style = HType.plex(17, FontWeight.Bold, 1.4f), color = HColors.ink)
        } else {
            Box(Modifier.background(HColors.bg, CircleShape).padding(7.dp)) {
                HIconView(type.icon, size = 18.dp, color = HColors.accent)
            }
        }
    }
}
