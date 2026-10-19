package com.sapphire.hawe.feature.groups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.model.AvatarContent
import com.sapphire.hawe.core.net.GroupRepository
import com.sapphire.hawe.core.net.toUserMessage
import com.sapphire.hawe.feature.mypage.avatarUploadBytes
import com.sapphire.hawe.core.model.choiceName
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.layout.HCtaScaffold
import com.sapphire.hawe.designsystem.tokens.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun GroupProfileScreen(id: Long, store: DataStore, groups: GroupRepository, session: AppSession,
    onBack: () -> Unit) {
    val group = store.group(id)
    var state by remember(id) { mutableStateOf<GroupProfileState?>(null) }
    val toast = rememberHToastState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        try {
            val detail = groups.detail(id, session.account.userId)
            store.applyServerGroup(detail)
            state = detail.me?.let { GroupProfileState.from(it, session.account.profile) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            toast.show(e.toUserMessage())
        }
    }
    val me = group?.me
    if (group == null || me == null) {
        Box(Modifier.fillMaxSize().background(HColors.bg))
        return
    }
    val editing = state
    if (editing == null) {
        Box(Modifier.fillMaxSize().background(HColors.bg)) {
            HNavBar("그룹 내 프로필", leading = HNavLeading.BACK, onLeading = onBack)
            HToastHost(toast)
        }
        return
    }
    val reselect = needsPhotoReselect(editing.useDefault,
        (editing.avatar as? AvatarContent.Photo)?.uri?.scheme, editing.nickname,
        editing.originalProfile?.nickname)
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
                        Text("마이페이지의 프로필(${editing.defaultProfile.avatar.choiceName} · ${editing.defaultProfile.nickname})을 그대로 써요",
                            style = HType.caption12, color = HColors.textSecondary)
                    }
                    HToggle(editing.useDefault, { state = editing.copy(useDefault = it) },
                        contentDescription = "기본 프로필 사용")
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HAvatar(editing.preview.avatar, size = 80.dp, characterScale = 0.8f)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("그룹 멤버에게 보이는 모습", style = HType.caption13, color = HColors.textSecondary)
                        Text(editing.preview.nickname, style = HType.plex(20, FontWeight.Bold), color = HColors.ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                AnimatedVisibility(!editing.useDefault) {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        CharacterPicker(editing.avatar, { state = editing.copy(avatar = it) },
                            cellSize = 64.dp, characterSize = 48.dp, columnSpacing = 22.dp, rowSpacing = 10.dp)
                        HTextField(editing.nickname, { state = editing.withNickname(it) }, "닉네임을 입력해 주세요",
                            label = "그룹 내 닉네임", maxLength = 10, textSize = 17)
                        if (reselect) Text("사진 프로필의 닉네임을 바꾸려면 사진을 다시 골라 주세요",
                            style = HType.caption13, color = HColors.danger)
                    }
                }
            }
        }, cta = {
            HButton(if (saving) "저장 중" else "저장", enabled = editing.canSave && !reselect && !saving &&
                editing.profileToSave != editing.originalProfile) {
                saving = true
                scope.launch {
                    try {
                        val profile = editing.profileToSave
                        if (profile == null) groups.clearGroupProfile(id)
                        else groups.saveGroupProfile(id, profile.avatar, profile.nickname) { avatarUploadBytes(context, it) }
                        store.applyServerGroup(groups.detail(id, session.account.userId))
                        onBack()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        toast.show(if (e is IOException) "사진을 불러오지 못했어요" else e.toUserMessage())
                    } finally { saving = false }
                }
            }
        })
    }; HToastHost(toast) }
}
