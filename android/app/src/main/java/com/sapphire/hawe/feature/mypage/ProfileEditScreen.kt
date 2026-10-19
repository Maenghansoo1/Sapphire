package com.sapphire.hawe.feature.mypage

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.net.AuthRepository
import com.sapphire.hawe.core.net.toUserMessage
import com.sapphire.hawe.core.model.choiceName
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.layout.HCtaScaffold
import com.sapphire.hawe.designsystem.tokens.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.IOException

@Composable
fun ProfileEditScreen(session: AppSession, store: DataStore, auth: AuthRepository, onBack: () -> Unit) {
    var state by remember { mutableStateOf(ProfileEditState(session.account.profile.avatar,
        session.account.profile.nickname)) }
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = rememberHToastState()
    var saving by remember { mutableStateOf(false) }
    Box {
    Column {
        Box(Modifier.padding(horizontal = HLayout.screenPadding)) {
            HNavBar("프로필 편집", leading = HNavLeading.BACK, onLeading = onBack)
        }
        HCtaScaffold(content = {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HAvatar(state.avatar, size = 88.dp, characterScale = 0.8f)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("선택한 프로필", style = HType.caption13, color = HColors.textSecondary)
                        Text(state.avatar.choiceName, style = HType.plex(20, FontWeight.Bold), color = HColors.ink)
                    }
                }
                CharacterPicker(state.avatar, { state = state.copy(avatar = it) })
                HTextField(state.nickname, { state = state.copy(nickname = it) },
                    "닉네임을 입력해 주세요", label = "닉네임", maxLength = 10, imeAction = ImeAction.Done)
            }
        }, cta = {
            HButton(if (saving) "저장 중" else "저장", enabled = state.canSave && !saving &&
                state.profileToSave != session.account.profile) {
                val profile = state.profileToSave
                saving = true
                scope.launch {
                    try {
                        val result = auth.saveProfile(session.account.profile, profile.nickname, profile.avatar) {
                            avatarUploadBytes(context, it)
                        }
                        result.account?.let { account ->
                            session.account = account
                            store.setMyProfile(account.profile)
                        }
                        if (result.error == null) {
                            focus.clearFocus()
                            onBack()
                        } else toast.show(if (result.error is IOException) "사진을 불러오지 못했어요"
                            else result.error.toUserMessage())
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        toast.show(e.toUserMessage())
                    } finally { saving = false }
                }
            }
        })
    }
    HToastHost(toast)
    }
}
