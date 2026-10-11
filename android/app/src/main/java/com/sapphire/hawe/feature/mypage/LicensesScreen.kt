package com.sapphire.hawe.feature.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.tokens.*

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(HColors.bg).padding(horizontal = HLayout.screenPaddingCompact)) {
        HNavBar("오픈소스 라이선스", leading = HNavLeading.BACK, onLeading = onBack)
        HListRow("IBM Plex Sans KR", value = "SIL Open Font License 1.1", height = 54.dp) {
            runCatching { openInCustomTab(context, "https://github.com/IBM/plex") }
        }
        HListRow("Fredoka", value = "SIL Open Font License 1.1", height = 54.dp) {
            runCatching { openInCustomTab(context, "https://github.com/hafontia-zz/Fredoka") }
        }
        Text("SIL Open Font License 1.1 전문은 openfontlicense.org 에서 볼 수 있어요.",
            Modifier.padding(top = 16.dp), style = HType.caption12, color = HColors.textSecondary)
    }
}
