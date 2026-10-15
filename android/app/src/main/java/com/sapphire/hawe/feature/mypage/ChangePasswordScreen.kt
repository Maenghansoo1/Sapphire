package com.sapphire.hawe.feature.mypage

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.layout.HCtaScaffold
import com.sapphire.hawe.designsystem.tokens.HLayout
import com.sapphire.hawe.core.net.ApiException
import com.sapphire.hawe.core.net.AuthRepository
import com.sapphire.hawe.core.net.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChangePasswordScreen(auth: AuthRepository, onBack: () -> Unit) {
    var state by remember { mutableStateOf(ChangePasswordState()) }
    var saving by remember { mutableStateOf(false) }
    var currentError by remember { mutableStateOf<String?>(null) }
    val toast = rememberHToastState()
    val scope = rememberCoroutineScope()
    Box {
    Column {
        Box(Modifier.padding(horizontal = HLayout.screenPadding)) {
            HNavBar("비밀번호 변경", leading = HNavLeading.BACK, onLeading = onBack)
        }
        HCtaScaffold(content = {
            Column(Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                HTextField(state.current, { state = state.copy(current = it); currentError = null }, "지금 쓰는 비밀번호",
                    label = "현재 비밀번호", isSecure = true, error = currentError, imeAction = ImeAction.Next)
                HTextField(state.new, { state = state.copy(new = it) }, "8자 이상, 영문·숫자 포함",
                    label = "새 비밀번호", isSecure = true, error = state.newError,
                    imeAction = ImeAction.Next)
                HTextField(state.confirm, { state = state.copy(confirm = it) }, "한 번 더 입력해 주세요",
                    label = "새 비밀번호 확인", isSecure = true, error = state.confirmError,
                    imeAction = ImeAction.Done)
            }
        }, cta = {
            HButton(if (saving) "변경 중" else "변경하기", enabled = state.canSave && !saving) {
                saving = true
                scope.launch {
                    try {
                        auth.changePassword(state.current, state.new)
                        toast.show("비밀번호를 바꿨어요")
                        delay(1600)
                        onBack()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        if (e is ApiException && e.code == "WRONG_CURRENT_PASSWORD")
                            currentError = "현재 비밀번호가 맞지 않아요"
                        else toast.show(e.toUserMessage())
                    } finally { saving = false }
                }
            }
        })
    }
    HToastHost(toast)
    }
}
