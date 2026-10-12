package com.sapphire.hawe.feature.groups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.COMING_SOON
import com.sapphire.hawe.core.model.choiceName
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.layout.HCtaScaffold
import com.sapphire.hawe.designsystem.tokens.*
import java.util.UUID

@Composable
fun GroupProfileScreen(id: Long, store: DataStore, onBack: () -> Unit) {
    val group = store.group(id)
    val me = group?.me
    if (group == null || me == null) {
        Box(Modifier.fillMaxSize().background(HColors.bg))
        return
    }
    var state by remember(id) { mutableStateOf(GroupProfileState.from(me)) }
    val toast = rememberHToastState()
    Box(Modifier.fillMaxSize().background(HColors.bg)) { Column {
        Box(Modifier.padding(horizontal = HLayout.screenPadding)) {
            HNavBar("그룹 내 프로필", leading = HNavLeading.BACK, onLeading = onBack)
        }
        HCtaScaffold(content = {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("'${group.name}' 그룹에서만 이렇게 보여요. 다른 그룹에는 영향이 없어요.",
                    style = HType.plex(14, lineHeightMultiple = 1.5f), color = HColors.textSecondary)
                Row(Modifier.fillMaxWidth().background(HColors.surface, RoundedCornerShape(HRadius.button))
                    .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("기본 프로필 사용", style = HType.plex(15, FontWeight.SemiBold), color = HColors.ink)
                        Text("마이페이지의 프로필(${state.defaultProfile.avatar.choiceName} · ${state.defaultProfile.nickname})을 그대로 써요",
                            style = HType.caption12, color = HColors.textSecondary)
                    }
                    HToggle(state.useDefault, { state = state.copy(useDefault = it) },
                        contentDescription = "기본 프로필 사용")
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HAvatar(state.preview.avatar, size = 80.dp, characterScale = 0.8f)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("그룹 멤버에게 보이는 모습", style = HType.caption13, color = HColors.textSecondary)
                        Text(state.preview.nickname, style = HType.plex(20, FontWeight.Bold), color = HColors.ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                AnimatedVisibility(!state.useDefault) {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        CharacterPicker(state.avatar, { state = state.copy(avatar = it) },
                            cellSize = 64.dp, characterSize = 48.dp, columnSpacing = 22.dp, rowSpacing = 10.dp)
                        HTextField(state.nickname, { state = state.withNickname(it) }, "닉네임을 입력해 주세요",
                            label = "그룹 내 닉네임", maxLength = 10, textSize = 17)
                    }
                }
            }
        }, cta = {
            HButton("저장", enabled = state.canSave) { toast.show(COMING_SOON) }
        })
    }; HToastHost(toast) }
}
