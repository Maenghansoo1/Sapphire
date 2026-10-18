package com.sapphire.hawe.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.R
import com.sapphire.hawe.app.AppLinks
import com.sapphire.hawe.app.AppSession
import com.sapphire.hawe.app.LocalSession
import com.sapphire.hawe.app.LocalStore
import com.sapphire.hawe.data.DataStore
import com.sapphire.hawe.app.MyAccount
import com.sapphire.hawe.app.rememberOpenWebLink
import com.sapphire.hawe.designsystem.CharacterPicker
import com.sapphire.hawe.designsystem.HButton
import com.sapphire.hawe.designsystem.HCTAScaffold
import com.sapphire.hawe.designsystem.HCheckbox
import com.sapphire.hawe.designsystem.HColors
import com.sapphire.hawe.designsystem.HImage
import com.sapphire.hawe.designsystem.HLayout
import com.sapphire.hawe.designsystem.HLogoLockup
import com.sapphire.hawe.designsystem.HNavBar
import com.sapphire.hawe.designsystem.HNavStep
import com.sapphire.hawe.designsystem.HRadius
import com.sapphire.hawe.designsystem.HScreen
import com.sapphire.hawe.designsystem.HTextField
import com.sapphire.hawe.designsystem.HType
import com.sapphire.hawe.designsystem.NavLeading
import com.sapphire.hawe.designsystem.ProfilePreviewRow
import com.sapphire.hawe.designsystem.bottomInsetPadding
import com.sapphire.hawe.designsystem.plainClickable
import com.sapphire.hawe.designsystem.plex
import com.sapphire.hawe.model.AvatarContent
import com.sapphire.hawe.model.LoginProvider
import com.sapphire.hawe.model.Profile
import com.sapphire.hawe.model.ProfileCharacter
import java.time.LocalDate

// MARK: - A1 로그인 (Figma 1:190)

@Composable
fun LoginScreen(onSignup: () -> Unit, onPasswordReset: (String) -> Unit, onSocial: (LoginProvider) -> Unit) {
    val session = LocalSession.current
    val store = LocalStore.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    HScreen {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HLayout.screenPadding)
                .padding(top = HLayout.introTopInset)
                .navigationBarsPadding()
                .padding(bottom = HLayout.ctaBottomInset),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HLogoLockup()
                Text("다시 만나서 반가워요", style = plex(28, FontWeight.Bold, lineHeight = 1.35f), color = HColors.ink)
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HTextField(email, { email = it }, placeholder = "이메일", keyboardType = KeyboardType.Email)
                HTextField(password, { password = it }, placeholder = "비밀번호", isSecure = true)
                HButton("로그인") { logIn(session, store, email) }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = HLayout.minTouch),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("회원가입", style = plex(14, FontWeight.SemiBold), color = HColors.ink, modifier = Modifier.plainClickable(onClick = onSignup))
                    Text("|", style = plex(14), color = HColors.controlOff)
                    Text("비밀번호 찾기", style = plex(14), color = HColors.textSecondary, modifier = Modifier.plainClickable { onPasswordReset(email) })
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(1.dp).background(HColors.line))
                Text("간편 로그인", style = HType.caption13, color = HColors.textSecondary)
                Box(Modifier.weight(1f).height(1.dp).background(HColors.line))
            }

            // TODO: 서버 연결 시 실제 간편 로그인. 지금은 신규 가입자로 보고 A3 로 가요.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialButton(LoginProvider.Apple) { onSocial(LoginProvider.Apple) }
                SocialButton(LoginProvider.Kakao) { onSocial(LoginProvider.Kakao) }
                SocialButton(LoginProvider.Google) { onSocial(LoginProvider.Google) }
            }
        }
    }
}

/**
 * TODO: 서버 연결 시 실제 로그인. 지금은 이 기기에서 가입한 이메일이면 그 계정으로,
 * 처음 보는 이메일이면 그 이메일로 가입된 계정이 있다고 보고 들어가요 (iOS 와 같은 규칙).
 */
private fun logIn(session: AppSession, store: DataStore, email: String) {
    val trimmed = email.trim().lowercase()
    if (trimmed.isNotEmpty() && session.account.email?.lowercase() != trimmed) {
        val nickname = trimmed.substringBefore("@").take(10).ifEmpty { "하늘" }
        val profile = Profile(AvatarContent.Character(ProfileCharacter.Default), nickname)
        session.account = MyAccount(profile, trimmed, LocalDate.now(), LoginProvider.Email, setOf(LoginProvider.Email))
        store.reset()
        store.updateMyProfile(profile)
    }
    session.signIn()
}

/** 간편 로그인 버튼 — 높이 52 · 모서리 14 · 아이콘 18 + 16 SemiBold */
@Composable
private fun SocialButton(provider: LoginProvider, onClick: () -> Unit) {
    val shape = RoundedCornerShape(HRadius.input)
    val (title, icon, background, foreground) = when (provider) {
        LoginProvider.Apple -> Quad("Apple로 계속하기", R.drawable.social_apple, HColors.ink, HColors.onAccent)
        LoginProvider.Kakao -> Quad("카카오로 계속하기", R.drawable.social_kakao, HColors.kakaoContainer, HColors.kakaoLabel)
        else -> Quad("Google로 계속하기", R.drawable.social_google, HColors.bg, HColors.ink)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(background, shape)
            .then(if (provider == LoginProvider.Google) Modifier.border(1.dp, HColors.border, shape) else Modifier)
            .plainClickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HImage(icon, 18.dp)
        Text(title, style = plex(16, FontWeight.SemiBold), color = foreground)
    }
}

private data class Quad(val title: String, val icon: Int, val background: Color, val foreground: Color)

// MARK: - A2 회원가입 · 계정 정보 (Figma 1:222)

/** 입력 검사: 이메일 형식 · 비밀번호(8자 이상, 영문·숫자) · 확인 일치 + [필수] 약관 2개 (사용자 결정) */
@Composable
fun SignupAccountScreen(onBack: () -> Unit, onNext: (String) -> Unit) {
    val openLink = rememberOpenWebLink()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var agreeService by rememberSaveable { mutableStateOf(false) }
    var agreePrivacy by rememberSaveable { mutableStateOf(false) }

    val emailValid = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+").matches(email)
    val passwordValid = password.length >= 8 && password.any { it.isLetter() && it.code < 128 } && password.any { it.isDigit() }
    val confirmValid = confirm.isNotEmpty() && confirm == password
    val canProceed = emailValid && passwordValid && confirmValid && agreeService && agreePrivacy

    HScreen {
        HNavBar(modifier = Modifier.padding(horizontal = HLayout.screenPadding), leading = NavLeading.Back(onBack)) { HNavStep("1 / 2") }
        HCTAScaffold(
            content = {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("계정을 만들어요", style = HType.screenTitle, color = HColors.ink)
                    HTextField(
                        email, { email = it }, placeholder = "example@email.com", label = "이메일", keyboardType = KeyboardType.Email,
                        error = if (email.isEmpty() || emailValid) null else "이메일 형식이 올바르지 않아요",
                    )
                    HTextField(
                        password, { password = it }, placeholder = "8자 이상, 영문·숫자 포함", label = "비밀번호", isSecure = true,
                        error = if (password.isEmpty() || passwordValid) null else "8자 이상, 영문과 숫자를 함께 써 주세요",
                    )
                    HTextField(
                        confirm, { confirm = it }, placeholder = "한 번 더 입력해 주세요", label = "비밀번호 확인", isSecure = true,
                        error = if (confirm.isEmpty() || confirm == password) null else "비밀번호가 일치하지 않아요",
                    )
                    // 약관 — 테두리 line · 모서리 16 · 좌우 14 · 위아래 6 ([선택] 새 소식 항목은 빼기로 했어요)
                    Column(
                        Modifier
                            .border(1.dp, HColors.line, RoundedCornerShape(HRadius.button))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        HCheckbox(agreeService && agreePrivacy, { agreeService = it; agreePrivacy = it }, Modifier.fillMaxWidth()) {
                            Text("전체 동의", style = plex(15, FontWeight.Bold), color = HColors.ink, modifier = Modifier.weight(1f))
                        }
                        TermRow("[필수] 서비스 이용약관 동의", agreeService, { agreeService = it }) { openLink(AppLinks.TERMS_OF_SERVICE) }
                        TermRow("[필수] 개인정보 처리방침 동의", agreePrivacy, { agreePrivacy = it }) { openLink(AppLinks.PRIVACY_POLICY) }
                    }
                }
            },
            cta = { HButton("다음", enabled = canProceed) { onNext(email.trim()) } },
        )
    }
}

@Composable
private fun TermRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, onDetail: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HCheckbox(checked, onChange, Modifier.weight(1f)) {
            Text(title, style = plex(14), color = HColors.ink, modifier = Modifier.weight(1f))
        }
        Text(
            "보기",
            style = HType.caption13.copy(textDecoration = TextDecoration.Underline),
            color = HColors.textSecondary,
            modifier = Modifier.heightIn(min = HLayout.minTouch).padding(top = 13.dp).plainClickable(onClick = onDetail),
        )
    }
}

// MARK: - A3 회원가입 · 프로필 (Figma 1:256)

/** 캐릭터 7종 + 내 사진 중 선택(기본 몽실이), 닉네임 2–10자 입력 시 '시작하기' 활성화 */
@Composable
fun SignupProfileScreen(provider: LoginProvider, email: String?, onBack: () -> Unit) {
    val session = LocalSession.current
    val store = LocalStore.current
    var avatar by remember { mutableStateOf<AvatarContent>(AvatarContent.Character(ProfileCharacter.Default)) }
    var nickname by rememberSaveable { mutableStateOf("") }
    val canStart = nickname.trim().length in 2..10

    HScreen {
        HNavBar(modifier = Modifier.padding(horizontal = HLayout.screenPadding), leading = NavLeading.Back(onBack)) {
            // 간편 로그인으로 바로 온 경우엔 A2 를 건너뛰어 "2 / 2" 를 숨겨요.
            if (provider == LoginProvider.Email) HNavStep("2 / 2")
        }
        HCTAScaffold(
            content = {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("어떻게 불러드릴까요?", style = HType.screenTitle, color = HColors.ink)
                        Text("캐릭터를 고르고 이름을 정해주세요.", style = HType.body15, color = HColors.textSecondary)
                    }
                    ProfilePreviewRow(avatar, "선택한 캐릭터", avatar.choiceName)
                    CharacterPicker(avatar, { avatar = it })
                    HTextField(
                        nickname, { nickname = it }, placeholder = "닉네임을 입력해 주세요", label = "닉네임",
                        helper = "캐릭터와 이름은 나중에 바꿀 수 있어요", maxLength = 10,
                    )
                }
            },
            cta = {
                // TODO: 서버 연결 시 프로필 저장. 간편 로그인 이메일은 각 SDK 에서 받아와요.
                HButton("시작하기", enabled = canStart) {
                    val profile = Profile(avatar, nickname.trim())
                    session.account = MyAccount(profile, email, LocalDate.now(), provider, setOf(provider))
                    // 새 계정이라 그룹이 없어서 01 시작으로 가요.
                    store.reset()
                    store.updateMyProfile(profile)
                    session.signIn()
                }
            },
        )
    }
}

// MARK: - 비밀번호 찾기 (Figma 없음 — 이메일로 재설정 링크, 사용자 결정)

@Composable
fun PasswordResetScreen(initialEmail: String, onBack: () -> Unit) {
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var sentTo by rememberSaveable { mutableStateOf<String?>(null) }

    HScreen {
        HNavBar(modifier = Modifier.padding(horizontal = HLayout.screenPadding), leading = NavLeading.Back(onBack))
        HCTAScaffold(
            content = {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    val address = sentTo
                    if (address == null) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("비밀번호를 잊으셨나요?", style = HType.screenTitle, color = HColors.ink)
                            Text("가입한 이메일로 비밀번호 재설정 링크를 보내드려요.", style = HType.body15, color = HColors.textSecondary)
                        }
                        HTextField(email, { email = it }, placeholder = "example@email.com", label = "이메일", keyboardType = KeyboardType.Email)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("메일을 보냈어요", style = HType.screenTitle, color = HColors.ink)
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = HColors.ink, fontWeight = FontWeight.SemiBold)) { append(address) }
                                    append("(으)로 보낸 링크를 눌러 새 비밀번호를 설정해 주세요. 메일이 보이지 않으면 스팸함도 확인해 주세요.")
                                },
                                style = HType.body15,
                                color = HColors.textSecondary,
                            )
                        }
                    }
                }
            },
            cta = {
                if (sentTo != null) {
                    HButton("로그인으로 돌아가기", onClick = onBack)
                    // TODO: 서버 연결 시 재전송 요청
                    HButton("다시 보내기", kind = com.sapphire.hawe.designsystem.HButtonKind.Secondary) {}
                } else {
                    // TODO: 서버 연결 시 재설정 메일 발송 요청
                    HButton("재설정 링크 보내기", enabled = email.isNotBlank()) { sentTo = email.trim() }
                }
            },
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun LoginPreview() {
    androidx.compose.runtime.CompositionLocalProvider(LocalSession provides AppSession(), LocalStore provides DataStore()) {
        LoginScreen({}, {}, {})
    }
}

@Preview(showSystemUi = true)
@Composable
private fun SignupProfilePreview() {
    androidx.compose.runtime.CompositionLocalProvider(LocalSession provides AppSession(), LocalStore provides DataStore()) {
        SignupProfileScreen(LoginProvider.Email, "a@b.com") {}
    }
}
