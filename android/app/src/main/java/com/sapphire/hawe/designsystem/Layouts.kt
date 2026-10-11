package com.sapphire.hawe.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import kotlinx.coroutines.delay

/** 화면 바탕 — 흰 배경 + 상태 막대 아래부터 + 키보드가 올라오면 그만큼 올려요. */
@Composable
fun HScreen(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .background(HColors.bg)
            .statusBarsPadding()
            .imePadding(),
        content = content,
    )
}

/** 하단 CTA 와 화면 아래 끝 사이를 Figma 값으로 맞춰요 (내비게이션 바가 있으면 그만큼 빼요). */
@Composable
fun bottomInsetPadding(total: Dp): Dp {
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return max(total - nav, 8.dp)
}

/**
 * 하단 고정 CTA 화면 틀 (CLAUDE.md §3-4)
 * 내용은 위쪽만 스크롤되고, CTA 는 화면 아래 끝에서 [bottomInset](기본 36) 위에 고정돼요.
 */
@Composable
fun ColumnScope.HCTAScaffold(
    horizontalPadding: Dp = HLayout.screenPadding,
    bottomInset: Dp = HLayout.ctaBottomInset,
    content: @Composable ColumnScope.() -> Unit,
    cta: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding)
            .padding(bottom = 24.dp),
        content = content,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = horizontalPadding)
            .padding(top = 12.dp, bottom = bottomInsetPadding(bottomInset)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = cta,
    )
}

/**
 * Figma 17-4 알림창 — 화면 어둡게(ink 45%) + 흰 카드(모서리 24 · 좌우 28 여백)
 * 제목 20 Bold, 내용 14 (줄 높이 1.6), 버튼 두 개(높이 50) — 취소는 gray, 확정은 danger
 */
@Composable
fun HAlert(
    title: String,
    message: String,
    confirmTitle: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    cancelTitle: String = "취소",
    confirmKind: HButtonKind = HButtonKind.Danger,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(HColors.ink.copy(alpha = 0.45f))
            .plainClickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .background(HColors.bg, RoundedCornerShape(HRadius.largeCard))
                .plainClickable {}
                .padding(top = 28.dp, bottom = 20.dp, start = 22.dp, end = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = plex(20, FontWeight.Bold), color = HColors.ink, textAlign = TextAlign.Center)
            Text(message, style = plex(14, lineHeight = 1.6f), color = HColors.textSecondary, textAlign = TextAlign.Center)
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HButton(cancelTitle, Modifier.weight(1f), kind = HButtonKind.Gray, height = 50.dp, onClick = onCancel)
                HButton(confirmTitle, Modifier.weight(1f), kind = confirmKind, height = 50.dp, onClick = onConfirm)
            }
        }
    }
}

/** 잠깐 떴다 사라지는 안내 — Figma 에 없어서 ink 알약 모양으로 정했어요 (iOS 와 같은 모양). */
@Composable
fun HToast(message: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(message) {
        if (message != null) {
            delay(1600)
            onDismiss()
        }
    }
    Box(modifier.fillMaxSize().padding(PaddingValues(bottom = 24.dp)).navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            Box(
                Modifier
                    .height(44.dp)
                    .background(HColors.ink.copy(alpha = 0.9f), CircleShape)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(message ?: "", style = plex(14, FontWeight.SemiBold), color = HColors.onAccent)
            }
        }
    }
}
