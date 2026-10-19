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
        // 그림 인식(A8): 학습 데이터 출처(CC BY 4.0 은 출처 표기가 조건)와 기기 안 추론 런타임
        HListRow("Quick, Draw! 데이터셋", value = "Google · CC BY 4.0", height = 54.dp) {
            runCatching { openInCustomTab(context, "https://github.com/googlecreativelab/quickdraw-dataset") }
        }
        HListRow("LiteRT (TensorFlow Lite)", value = "Apache License 2.0", height = 54.dp) {
            runCatching { openInCustomTab(context, "https://github.com/google-ai-edge/LiteRT") }
        }
        Text("SIL Open Font License 1.1 전문은 openfontlicense.org 에서 볼 수 있어요.",
            Modifier.padding(top = 16.dp), style = HType.caption12, color = HColors.textSecondary)
        Text("그림 인식 모델은 Quick, Draw! 데이터셋(creativecommons.org/licenses/by/4.0)으로 HA'WE 팀이 직접 학습했어요. " +
            "AI 추측은 재미용이며, 그림은 휴대폰 안에서 분석하고 추측 후보만 그룹에 공유해요.",
            Modifier.padding(top = 8.dp), style = HType.caption12, color = HColors.textSecondary)
    }
}
