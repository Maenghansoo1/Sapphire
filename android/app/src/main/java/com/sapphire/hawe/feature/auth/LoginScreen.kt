package com.sapphire.hawe.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.R
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.COMING_SOON
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.model.LoginProvider
import com.sapphire.hawe.core.net.AuthRepository
import com.sapphire.hawe.core.net.toUserMessage
import com.sapphire.hawe.designsystem.brand.HLogoLockup
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.layout.ctaBottomPadding
import com.sapphire.hawe.designsystem.tokens.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(session: AppSession, store: DataStore, auth: AuthRepository, onSignup: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val toast = rememberHToastState()
    var loading by remember { mutableStateOf(false) }
    val bottomPadding = ctaBottomPadding()
    val login = {
        if (!loading && email.isNotBlank() && password.isNotEmpty()) {
            loading = true
            scope.launch {
                try {
                    val account = auth.login(email, password)
                    store.reset()
                    store.setMyProfile(account.profile)
                    focus.clearFocus()
                    session.signIn(account)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    toast.show(e.toUserMessage())
                } finally {
                    loading = false
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(HColors.bg).imePadding().verticalScroll(rememberScrollState())
        .padding(horizontal = HLayout.screenPadding).padding(top = HLayout.introTopInset,
            bottom = bottomPadding), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HLogoLockup()
            Text("다시 만나서 반가워요", style = HType.plex(28, FontWeight.Bold, 1.35f), color = HColors.ink)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HTextField(email, { email = it }, "이메일", keyboardType = KeyboardType.Email,
                capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Next)
            HTextField(password, { password = it }, "비밀번호", isSecure = true,
                capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                imeAction = ImeAction.Done, onImeAction = login)
            HButton("로그인", enabled = !loading && email.isNotBlank() && password.isNotEmpty(), onClick = login)
            Row(Modifier.fillMaxWidth().heightIn(min = HLayout.minTouch), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                Text("회원가입", Modifier.clickable(onClick = onSignup),
                    style = HType.plex(14, FontWeight.SemiBold), color = HColors.ink)
                Spacer(Modifier.width(16.dp))
                Text("|", style = HType.plex(14), color = HColors.controlOff)
                Spacer(Modifier.width(16.dp))
                Text("비밀번호 찾기", Modifier.clickable { toast.show(COMING_SOON) },
                    style = HType.plex(14), color = HColors.textSecondary)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f).height(1.dp).background(HColors.line))
            Text("간편 로그인", style = HType.caption13, color = HColors.textSecondary)
            Box(Modifier.weight(1f).height(1.dp).background(HColors.line))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SocialLoginButton(LoginProvider.APPLE, onClick = { toast.show(COMING_SOON) })
            SocialLoginButton(LoginProvider.KAKAO, onClick = { toast.show(COMING_SOON) })
            SocialLoginButton(LoginProvider.GOOGLE, onClick = { toast.show(COMING_SOON) })
        }
    }
    HToastHost(toast)
    }
}

@Composable
private fun SocialLoginButton(provider: LoginProvider, onClick: () -> Unit) {
    val title = when (provider) {
        LoginProvider.APPLE -> "Apple로 계속하기"
        LoginProvider.KAKAO -> "카카오로 계속하기"
        LoginProvider.GOOGLE -> "Google로 계속하기"
        LoginProvider.EMAIL -> return
    }
    val icon = when (provider) {
        LoginProvider.APPLE -> R.drawable.social_apple
        LoginProvider.KAKAO -> R.drawable.social_kakao
        else -> R.drawable.social_google
    }
    val bg = when (provider) {
        LoginProvider.APPLE -> HColors.ink
        LoginProvider.KAKAO -> HColors.kakaoContainer
        else -> HColors.bg
    }
    val fg = when (provider) {
        LoginProvider.APPLE -> HColors.onAccent
        LoginProvider.KAKAO -> HColors.kakaoLabel
        else -> HColors.ink
    }
    val shape = RoundedCornerShape(HRadius.input)
    Row(Modifier.fillMaxWidth().height(52.dp).background(bg, shape)
        .then(if (provider == LoginProvider.GOOGLE) Modifier.border(1.dp, HColors.border, shape) else Modifier)
        .clickable(onClick = onClick), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(icon), null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = HType.plex(16, FontWeight.SemiBold), color = fg)
    }
}
