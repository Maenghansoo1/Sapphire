package com.sapphire.hawe.feature.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.brand.HLogoSymbol
import com.sapphire.hawe.designsystem.brand.HWordmark

/** 00 스플래시 — 흰 바탕 가운데에 심볼(48) + 워드마크(38)를 가로로 나란히, 간격 10 (Figma `00 스플래시` 대비 75% 축소) */
@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(HColors.bg), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            HLogoSymbol(48.dp)
            HWordmark(38)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SplashScreenPreview() {
    SplashScreen()
}
