package com.sapphire.hawe.feature.groups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.model.GroupInvitePreview
import com.sapphire.hawe.core.model.InviteCode
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.layout.HCtaScaffold
import com.sapphire.hawe.designsystem.tokens.*

@Composable
fun JoinGroupScreen(store: DataStore, initialCode: String?, onBack: () -> Unit, onJoined: (String) -> Boolean) {
    var state by remember { mutableStateOf(JoinGroupState()) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var focused by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(initialCode) {
        if (!initialCode.isNullOrEmpty()) {
            state = state.withInitialCode(initialCode, store::lookUpInvite)
            field = TextFieldValue(state.code, selection = TextRange(state.code.length))
        }
        if (state.code.length < InviteCode.LENGTH) requester.requestFocus()
    }
    Column(Modifier.background(HColors.bg)) {
        Box(Modifier.padding(horizontal = HLayout.screenPadding)) {
            HNavBar(leading = HNavLeading.BACK, onLeading = onBack)
        }
        HCtaScaffold(bottomInset = 40.dp, content = {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("초대 코드 입력", style = HType.screenTitle, color = HColors.ink)
                    Text("친구에게 받은 6자리 코드를 입력해 주세요.", style = HType.body15,
                        color = HColors.textSecondary)
                }
                Box {
                    Row(Modifier.fillMaxWidth().clickable {
                        requester.requestFocus(); keyboard?.show()
                    }.semantics { contentDescription = "초대 코드" },
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(InviteCode.LENGTH) { index ->
                            val current = focused && index == state.code.length
                            Box(Modifier.weight(1f).height(58.dp)
                                .background(if (current) HColors.bg else HColors.surface, RoundedCornerShape(HRadius.input))
                                .then(if (current) Modifier.border(1.5.dp, HColors.ink, RoundedCornerShape(HRadius.input)) else Modifier),
                                contentAlignment = Alignment.Center) {
                                Text(state.code.getOrNull(index)?.toString() ?: "", style = HType.plex(24, FontWeight.Bold),
                                    color = HColors.ink)
                            }
                        }
                    }
                    BasicTextField(field, onValueChange = { incoming ->
                        val normalized = InviteCode.sanitize(incoming.text)
                        field = if (normalized == incoming.text) incoming else
                            TextFieldValue(normalized, selection = TextRange(normalized.length))
                        state = state.withCode(normalized, store::lookUpInvite)
                        if (normalized.length == InviteCode.LENGTH) { focus.clearFocus(); keyboard?.hide() }
                    }, modifier = Modifier.size(1.dp).alpha(0f).focusRequester(requester)
                        .onFocusChanged { focused = it.isFocused },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii,
                            capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                        singleLine = true)
                }
                Text(state.errorMessage ?: "링크로 받았다면 링크를 누르면 자동으로 입력돼요.",
                    style = HType.caption13, color = if (state.errorMessage == null) HColors.textSecondary else HColors.danger)
                AnimatedVisibility(state.preview != null) {
                    state.preview?.let { GroupInviteCard(it) }
                }
            }
        }, cta = {
            HButton("참여하기", enabled = state.preview != null && !submitted) {
                if (!submitted) {
                    submitted = true
                    if (!onJoined(state.code)) submitted = false
                }
            }
        })
    }
}

@Composable
private fun GroupInviteCard(preview: GroupInvitePreview) {
    Column(Modifier.fillMaxWidth().border(1.dp, HColors.line, RoundedCornerShape(18.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("참여할 그룹", style = HType.plex(13, FontWeight.SemiBold), color = HColors.textSecondary)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(preview.name, style = HType.plex(18, FontWeight.Bold), color = HColors.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${preview.relationship.displayName} · ${preview.members.size} / ${preview.relationship.maxMembers}명 참여 중",
                style = HType.plex(14), color = HColors.textSecondary)
        }
        MemberRow(preview.members, preview.relationship, avatarSize = 32.dp)
    }
}
