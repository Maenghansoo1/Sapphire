package com.sapphire.hawe.designsystem.layout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.Text
import com.sapphire.hawe.designsystem.components.HButton
import com.sapphire.hawe.designsystem.tokens.HLayout

@Composable
fun HCtaScaffold(horizontalPadding: Dp = HLayout.screenPadding, bottomInset: Dp = HLayout.ctaBottomInset,
    content: @Composable () -> Unit, cta: @Composable () -> Unit) {
    val ctaBottom = ctaBottomPadding(bottomInset)
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding).padding(bottom = 24.dp)) { content() }
        Column(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding)
            .padding(top = 12.dp, bottom = ctaBottom)) { cta() }
    }
}

@Composable
fun ctaBottomPadding(bottomInset: Dp = HLayout.ctaBottomInset): Dp {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    return maxOf(bottomInset, (if (keyboardOpen) 0.dp else navBottom) + 12.dp)
}

@Preview(showBackground = true)
@Composable
private fun CtaScaffoldPreview() {
    HCtaScaffold(content = { Text("怨꾩젙??留뚮뱾?댁슂") }, cta = { HButton("?ㅼ쓬") {} })
}
