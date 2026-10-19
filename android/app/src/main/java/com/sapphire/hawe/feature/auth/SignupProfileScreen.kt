package com.sapphire.hawe.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.net.ApiException
import com.sapphire.hawe.core.net.AuthRepository
import com.sapphire.hawe.core.net.toUserMessage
import com.sapphire.hawe.core.model.*
import com.sapphire.hawe.designsystem.assets.ProfileCharacter
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.layout.HCtaScaffold
import com.sapphire.hawe.designsystem.tokens.*
import com.sapphire.hawe.feature.mypage.avatarUploadBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SignupProfileScreen(provider: LoginProvider, email: String?, session: AppSession, store: DataStore,
    password: String, agreeTerms: Boolean, agreePrivacy: Boolean, auth: AuthRepository, onBack: () -> Unit) {
    var avatar by remember { mutableStateOf<AvatarContent>(AvatarContent.Character(ProfileCharacter.MONGSIL)) }
    var nickname by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = rememberHToastState()
    var loading by remember { mutableStateOf(false) }
    var registered by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    Column {
        Box(Modifier.padding(horizontal = HLayout.screenPadding)) {
            HNavBar(leading = HNavLeading.BACK, onLeading = onBack,
                trailing = { if (provider == LoginProvider.EMAIL) HNavStep("2 / 2") })
        }
        HCtaScaffold(content = {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("어떻게 불러드릴까요?", style = HType.screenTitle, color = HColors.ink)
                    Text("캐릭터나 사진을 고르고 이름을 정해주세요.", style = HType.body15, color = HColors.textSecondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HAvatar(avatar, size = 88.dp, characterScale = 0.8f)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("선택한 프로필", style = HType.caption13, color = HColors.textSecondary)
                        Text(avatar.choiceName, style = HType.plex(20, FontWeight.Bold), color = HColors.ink)
                    }
                }
                CharacterPicker(avatar, { avatar = it })
                HTextField(nickname, { nickname = it }, "닉네임을 입력해 주세요", label = "닉네임",
                    helper = "캐릭터와 이름은 나중에 바꿀 수 있어요", maxLength = 10, imeAction = ImeAction.Done)
            }
        }, cta = {
            HButton("시작하기", enabled = !loading && nickname.trim().length in 2..10) {
                if (loading) return@HButton
                loading = true
                scope.launch {
                    try {
                        val account = auth.signUp(email.orEmpty(), password, nickname, avatar,
                            agreeTerms, agreePrivacy, registered, { registered = true }) {
                            avatarUploadBytes(context, it)
                        }
                        store.reset()
                        store.setMyProfile(account.profile)
                        focus.clearFocus()
                        session.signIn(account)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        toast.show(e.toUserMessage())
                        if (e is ApiException && e.code == "EMAIL_TAKEN") {
                            delay(1600)
                            onBack()
                        }
                    } finally {
                        loading = false
                    }
                }
            }
        })
    }
    HToastHost(toast)
    }
}
