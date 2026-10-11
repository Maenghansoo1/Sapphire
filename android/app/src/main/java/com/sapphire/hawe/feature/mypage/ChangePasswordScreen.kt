package com.sapphire.hawe.feature.mypage

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.layout.HCtaScaffold
import com.sapphire.hawe.designsystem.tokens.HLayout

@Composable
fun ChangePasswordScreen(onBack: () -> Unit) {
    var state by remember { mutableStateOf(ChangePasswordState()) }
    Column {
        Box(Modifier.padding(horizontal = HLayout.screenPadding)) {
            HNavBar("비밀번호 변경", leading = HNavLeading.BACK, onLeading = onBack)
        }
        HCtaScaffold(content = {
            Column(Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                HTextField(state.current, { state = state.copy(current = it) }, "지금 쓰는 비밀번호",
                    label = "현재 비밀번호", isSecure = true, imeAction = ImeAction.Next)
                HTextField(state.new, { state = state.copy(new = it) }, "8자 이상, 영문·숫자 포함",
                    label = "새 비밀번호", isSecure = true, error = state.newError,
                    imeAction = ImeAction.Next)
                HTextField(state.confirm, { state = state.copy(confirm = it) }, "한 번 더 입력해 주세요",
                    label = "새 비밀번호 확인", isSecure = true, error = state.confirmError,
                    imeAction = ImeAction.Done)
            }
        }, cta = {
            HButton("변경하기", enabled = state.canSave) {
                // TODO: 서버 연결 시 현재 비밀번호 확인과 변경을 요청해요.
                onBack()
            }
        })
    }
}
