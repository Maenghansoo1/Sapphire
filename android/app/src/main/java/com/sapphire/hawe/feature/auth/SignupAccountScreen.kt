package com.sapphire.hawe.feature.auth

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.AppLinks
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.layout.HCtaScaffold
import com.sapphire.hawe.designsystem.tokens.*

@Composable
fun SignupAccountScreen(password: String, onPasswordChange: (String) -> Unit,
    confirm: String, onConfirmChange: (String) -> Unit,
    onBack: () -> Unit, onNext: (String, Boolean, Boolean) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var service by rememberSaveable { mutableStateOf(false) }
    var privacy by rememberSaveable { mutableStateOf(false) }
    val state = SignupAccountState(email, password, confirm, service, privacy)
    val context = LocalContext.current
    Column {
        Box(Modifier.padding(horizontal = HLayout.screenPadding)) {
            HNavBar(leading = HNavLeading.BACK, onLeading = onBack, trailing = { HNavStep("1 / 2") })
        }
        HCtaScaffold(content = {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("계정을 만들어요", style = HType.screenTitle, color = HColors.ink)
                HTextField(email, { email = it }, "example@email.com", label = "이메일", error = state.emailError,
                    keyboardType = KeyboardType.Email, capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false, imeAction = ImeAction.Next)
                HTextField(password, onPasswordChange, "8자 이상, 영문·숫자 포함", label = "비밀번호",
                    isSecure = true, error = state.passwordError, capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false, imeAction = ImeAction.Next)
                HTextField(confirm, onConfirmChange, "한 번 더 입력해 주세요", label = "비밀번호 확인",
                    isSecure = true, error = state.confirmError, capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false, imeAction = ImeAction.Done)
                Column(Modifier.fillMaxWidth().border(1.dp, HColors.line, RoundedCornerShape(HRadius.button))
                    .padding(horizontal = 14.dp, vertical = 6.dp)) {
                    HCheckbox(state.agreeAll, { service = it; privacy = it }, Modifier.fillMaxWidth()) {
                        Text("전체 동의", style = HType.plex(15, FontWeight.Bold), color = HColors.ink)
                    }
                    AgreementRow("[필수] 서비스 이용약관 동의", service, { service = it }) {
                        openInCustomTab(context, AppLinks.termsOfService)
                    }
                    AgreementRow("[필수] 개인정보 처리방침 동의", privacy, { privacy = it }) {
                        openInCustomTab(context, AppLinks.privacyPolicy)
                    }
                }
            }
        }, cta = { HButton("다음", enabled = state.canProceed) { onNext(email.trim(), service, privacy) } })
    }
}

@Composable
private fun AgreementRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, onView: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        HCheckbox(checked, onChange, Modifier.weight(1f)) {
            Text(title, style = HType.plex(14), color = HColors.ink)
        }
        Box(Modifier.heightIn(min = HLayout.minTouch)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onView)
            .padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Text("보기", style = HType.caption13.copy(textDecoration = TextDecoration.Underline), color = HColors.textSecondary)
        }
    }
}
