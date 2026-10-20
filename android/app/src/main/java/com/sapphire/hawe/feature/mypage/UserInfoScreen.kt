package com.sapphire.hawe.feature.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.tokens.*
import com.sapphire.hawe.feature.groups.dotDate

@Composable
fun UserInfoScreen(session: AppSession, onBack: () -> Unit, onProfileEdit: () -> Unit,
    onChangePassword: () -> Unit) {
    val account = session.account
    Column(Modifier.fillMaxSize().background(HColors.bg).padding(horizontal = HLayout.screenPaddingCompact)) {
        HNavBar("유저 정보", leading = HNavLeading.BACK, onLeading = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionTitle("기본 정보", 14.dp)
            HListRow("닉네임", value = account.profile.nickname, height = 56.dp, onClick = onProfileEdit)
            InfoRow("이메일", account.email?.takeIf { it.isNotBlank() } ?: "없음")
            InfoRow("가입일", dotDate(account.joinedAt))
            SectionTitle("보안", 26.dp)
            Row(Modifier.fillMaxWidth().height(60.dp)
                .clickable(enabled = account.canChangePassword, onClick = onChangePassword),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("비밀번호 변경", style = HType.plex(15),
                        color = if (account.canChangePassword) HColors.ink else HColors.textTertiary)
                    Text("이메일로 가입한 계정만 사용할 수 있어요", style = HType.caption12,
                        color = HColors.textSecondary)
                }
                HIconView(HIcon.CHEVRON_RIGHT, size = 16.dp, color = HColors.textTertiary)
            }
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(HColors.line))
        }
    }
}

@Composable
private fun SectionTitle(title: String, top: androidx.compose.ui.unit.Dp) {
    Text(title, Modifier.padding(top = top, bottom = 4.dp),
        style = HType.plex(13, FontWeight.SemiBold), color = HColors.textSecondary)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = HType.plex(15), color = HColors.ink)
            Spacer(Modifier.weight(1f))
            Text(value, Modifier.weight(1.5f, fill = false), style = HType.plex(15),
                color = HColors.textBody, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(HColors.line))
    }
}
