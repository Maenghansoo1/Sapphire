package com.sapphire.hawe.feature.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.layout.ctaBottomPadding
import com.sapphire.hawe.designsystem.tokens.*

@Composable
fun WithdrawScreen(session: AppSession, store: DataStore, onBack: () -> Unit) {
    var state by remember { mutableStateOf(WithdrawState()) }
    Column(Modifier.fillMaxSize().background(HColors.bg).padding(horizontal = HLayout.screenPaddingCompact)) {
        HNavBar("회원 탈퇴", leading = HNavLeading.BACK, onLeading = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("정말 떠나시나요?", style = HType.plex(22, FontWeight.Bold), color = HColors.ink)
                Text("탈퇴하기 전에 아래 내용을 꼭 확인해 주세요.",
                    style = HType.plex(14), color = HColors.textSecondary)
            }
            Column(Modifier.fillMaxWidth().background(HColors.surface, RoundedCornerShape(16.dp))
                .padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    "그동안 남긴 그림, 사진, 글과 댓글이 모두 삭제되고 되돌릴 수 없어요.",
                    "참여 중인 모든 그룹에서 나가게 돼요. 방장인 그룹은 가장 오래된 멤버에게 방장이 넘어가요.",
                    "같은 계정으로 다시 가입해도 이전 기록은 복구되지 않아요."
                ).forEach { warning ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Spacer(Modifier.padding(top = 8.dp).size(5.dp).background(HColors.danger, CircleShape))
                        Text(warning, style = HType.plex(14, lineHeightMultiple = 1.5f), color = HColors.ink)
                    }
                }
            }
        }
        Column(Modifier.padding(bottom = ctaBottomPadding(32.dp))) {
            HCheckbox(state.agreed, { state = state.copy(agreed = it) }) {
                Text("위 내용을 모두 확인했고, 탈퇴에 동의해요", style = HType.plex(14), color = HColors.ink)
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HButton("계속 쓸게요", Modifier.weight(1f), HButtonKind.GRAY, 54.dp, onClick = onBack)
                HButton("탈퇴하기", Modifier.weight(1f), HButtonKind.DANGER, 54.dp,
                    enabled = state.canWithdraw) {
                    store.reset()
                    session.withdraw()
                }
            }
        }
    }
}
