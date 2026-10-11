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
import com.sapphire.hawe.core.model.*
import com.sapphire.hawe.designsystem.assets.ProfileCharacter
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.layout.HCtaScaffold
import com.sapphire.hawe.designsystem.tokens.*
import java.time.Instant

@Composable
fun SignupProfileScreen(provider: LoginProvider, email: String?, session: AppSession, store: DataStore,
    onBack: () -> Unit) {
    var avatar by remember { mutableStateOf<AvatarContent>(AvatarContent.Character(ProfileCharacter.MONGSIL)) }
    var nickname by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    Column {
        Box(Modifier.padding(horizontal = HLayout.screenPadding)) {
            HNavBar(leading = HNavLeading.BACK, onLeading = onBack,
                trailing = { if (provider == LoginProvider.EMAIL) HNavStep("2 / 2") })
        }
        HCtaScaffold(content = {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("어떻게 불러드릴까요?", style = HType.screenTitle, color = HColors.ink)
                    Text("캐릭터를 고르고 이름을 정해주세요.", style = HType.body15, color = HColors.textSecondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HAvatar(avatar, size = 88.dp, characterScale = 0.8f)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("선택한 캐릭터", style = HType.caption13, color = HColors.textSecondary)
                        Text(avatar.choiceName, style = HType.plex(20, FontWeight.Bold), color = HColors.ink)
                    }
                }
                CharacterPicker(avatar, { avatar = it })
                HTextField(nickname, { nickname = it }, "닉네임을 입력해 주세요", label = "닉네임",
                    helper = "캐릭터와 이름은 나중에 바꿀 수 있어요", maxLength = 10, imeAction = ImeAction.Done)
            }
        }, cta = {
            HButton("시작하기", enabled = nickname.trim().length in 2..10) {
                val profile = Profile(avatar, nickname.trim())
                // TODO: 간편 로그인 이메일은 서버 연결 시 각 SDK에서 받아와요.
                session.account = MyAccount(profile, email, Instant.now(), provider, setOf(provider))
                store.reset()
                store.setMyProfile(profile)
                focus.clearFocus()
                session.signIn()
            }
        })
    }
}
