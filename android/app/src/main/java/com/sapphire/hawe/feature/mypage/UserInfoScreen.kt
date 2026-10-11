package com.sapphire.hawe.feature.mypage

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.R
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.model.LoginProvider
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.tokens.*
import com.sapphire.hawe.feature.groups.dotDate

@Composable
fun UserInfoScreen(session: AppSession, onBack: () -> Unit, onProfileEdit: () -> Unit,
    onChangePassword: () -> Unit) {
    val account = session.account
    var unlink by remember { mutableStateOf<LoginProvider?>(null) }
    Column(Modifier.fillMaxSize().background(HColors.bg).padding(horizontal = HLayout.screenPaddingCompact)) {
        HNavBar("유저 정보", leading = HNavLeading.BACK, onLeading = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionTitle("기본 정보", 14.dp)
            HListRow("닉네임", value = account.profile.nickname, height = 56.dp, onClick = onProfileEdit)
            InfoRow("이메일", account.email?.takeIf { it.isNotBlank() } ?: "없음")
            InfoRow("가입일", dotDate(account.joinedAt))
            SectionTitle("로그인 연결", 26.dp)
            Text("여러 방법을 연결해 두면 어떤 방법으로든 같은 계정에 로그인할 수 있어요.",
                Modifier.padding(bottom = 4.dp), style = HType.plex(12, lineHeightMultiple = 1.5f),
                color = HColors.textSecondary)
            listOf(LoginProvider.KAKAO, LoginProvider.APPLE, LoginProvider.GOOGLE).forEach { provider ->
                ProviderRow(provider, providerRowState(account, provider),
                    onLink = { session.account = session.account.linking(provider) },
                    onUnlink = { unlink = provider })
            }
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
    unlink?.let { provider ->
        HAlert("${provider.displayName} 연결을 해제할까요?", "해제하면 이 방법으로는 로그인할 수 없어요.",
            "연결 해제", onCancel = { unlink = null }, onConfirm = {
                session.account = session.account.unlinking(provider)
                unlink = null
            })
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

@Composable
private fun ProviderRow(provider: LoginProvider, state: ProviderRowState,
    onLink: () -> Unit, onUnlink: () -> Unit) {
    val icon = when (provider) {
        LoginProvider.KAKAO -> R.drawable.social_kakao
        LoginProvider.APPLE -> R.drawable.social_apple
        else -> R.drawable.social_google
    }
    val bg = when (provider) {
        LoginProvider.KAKAO -> HColors.kakaoContainer
        LoginProvider.APPLE -> HColors.ink
        else -> HColors.bg
    }
    Column {
        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).background(bg, CircleShape)
                .then(if (provider == LoginProvider.GOOGLE) Modifier.border(1.dp, HColors.border, CircleShape)
                    else Modifier), contentAlignment = Alignment.Center) {
                Image(painterResource(icon), null, Modifier.size(16.dp))
            }
            Text(provider.displayName, Modifier.weight(1f), style = HType.plex(15), color = HColors.ink)
            when (state) {
                is ProviderRowState.Linked -> Box(Modifier.height(44.dp)
                    .alpha(if (state.canUnlink) 1f else 0.4f)
                    .semantics { if (!state.canUnlink) contentDescription = "마지막 로그인 방법이라 해제할 수 없어요" }
                    .clickable(enabled = state.canUnlink, onClick = onUnlink), contentAlignment = Alignment.Center) {
                    Text("연결됨", style = HType.plex(13, FontWeight.SemiBold), color = HColors.ink)
                }
                ProviderRowState.NotLinked -> Box(Modifier.height(44.dp).clickable(onClick = onLink),
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.height(34.dp).border(1.dp, HColors.border, CircleShape)
                        .padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text("연결하기", style = HType.plex(13, FontWeight.SemiBold), color = HColors.ink)
                    }
                }
            }
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(HColors.line))
    }
}
