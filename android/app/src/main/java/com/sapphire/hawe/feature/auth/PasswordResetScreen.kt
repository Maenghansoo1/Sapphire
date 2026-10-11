package com.sapphire.hawe.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.layout.HCtaScaffold
import com.sapphire.hawe.designsystem.tokens.*

@Composable
fun PasswordResetScreen(initialEmail: String, onBack: () -> Unit) {
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var sentTo by rememberSaveable { mutableStateOf<String?>(null) }
    Column {
        Box(Modifier.padding(horizontal = HLayout.screenPadding)) {
            HNavBar(leading = HNavLeading.BACK, onLeading = onBack)
        }
        HCtaScaffold(content = {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (sentTo == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("비밀번호를 잊으셨나요?", style = HType.screenTitle, color = HColors.ink)
                        Text("가입한 이메일로 비밀번호 재설정 링크를 보내드려요.",
                            style = HType.body15, color = HColors.textSecondary)
                    }
                    HTextField(email, { email = it }, "example@email.com", label = "이메일",
                        keyboardType = KeyboardType.Email, capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false, imeAction = ImeAction.Done)
                } else {
                    Text("메일을 보냈어요", style = HType.screenTitle, color = HColors.ink)
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(color = HColors.ink, fontWeight = FontWeight.SemiBold)) { append(sentTo) }
                        append("(으)로 보낸 링크를 눌러 새 비밀번호를 설정해 주세요. 메일이 보이지 않으면 스팸함도 확인해 주세요.")
                    }, style = HType.body15, color = HColors.textSecondary)
                }
            }
        }, cta = {
            if (sentTo == null) HButton("재설정 링크 보내기", enabled = email.trim().isNotEmpty()) {
                // TODO: 서버 연결 시 재설정 메일 발송 요청
                sentTo = email.trim()
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HButton("로그인으로 돌아가기") { onBack() }
                // TODO: 서버 연결 시 재전송 요청
                HButton("다시 보내기", kind = HButtonKind.SECONDARY) {}
            }
        })
    }
}
