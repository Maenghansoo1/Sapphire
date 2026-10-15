package com.sapphire.hawe.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.net.ApiException
import com.sapphire.hawe.core.net.GroupRepository
import com.sapphire.hawe.core.net.toUserMessage
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.tokens.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 임시 UI: 정식 후임자 선택 디자인이 나오기 전까지 기존 컴포넌트로 선택 흐름을 제공한다.
@Composable
fun SuccessorPickerScreen(id: Long, store: DataStore, groups: GroupRepository, session: AppSession,
    onBack: () -> Unit, onLeft: () -> Unit) {
    val group = store.group(id)
    val candidates = group?.members.orEmpty().filterNot { it.isMe }
    var selected by remember(id) { mutableStateOf<Long?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val toast = rememberHToastState()
    LaunchedEffect(id) {
        try {
            store.applyServerGroup(groups.detail(id, session.account.userId))
            if (store.group(id)?.members?.count { !it.isMe } == 0) {
                toast.show("다른 멤버가 없어졌어요. 그룹 정보를 다시 확인해 주세요")
                delay(1200)
                onBack()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is ApiException && e.code in setOf("GROUP_NOT_FOUND", "NOT_GROUP_MEMBER")) {
                store.leave(id); onLeft()
            } else toast.show(e.toUserMessage())
        }
    }
    val target = candidates.firstOrNull { it.id == selected }
    Box(Modifier.fillMaxSize().background(HColors.bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = HLayout.screenPaddingCompact)) {
            HNavBar("그룹장 넘기기", leading = HNavLeading.BACK, onLeading = onBack)
            Text("그룹장을 받을 멤버를 골라 주세요", Modifier.padding(top = 20.dp, bottom = 16.dp),
                style = HType.plex(18, FontWeight.Bold), color = HColors.ink)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                candidates.forEach { member ->
                    Row(Modifier.fillMaxWidth().border(1.dp,
                        if (selected == member.id) HColors.ink else HColors.border, RoundedCornerShape(12.dp))
                        .clickable { selected = member.id }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HAvatar(member.displayed.avatar, size = 40.dp)
                        Text(member.displayed.nickname, Modifier.weight(1f), style = HType.body15, color = HColors.ink)
                        HCheckboxMark(selected == member.id)
                    }
                }
            }
            HButton(if (leaving) "나가는 중" else "넘기고 나가기", kind = HButtonKind.DANGER,
                enabled = target != null && !leaving) { confirm = true }
            Spacer(Modifier.height(24.dp))
        }
        HToastHost(toast)
    }
    if (confirm && target != null) HAlert("${target.displayed.nickname}님에게 그룹장을 넘기고 나갈까요?",
        "그룹에서 나가도 내가 올린 작품과 댓글은 남아요.", "넘기고 나가기",
        onCancel = { confirm = false }, onConfirm = {
            if (!leaving) {
                leaving = true; confirm = false
                scope.launch {
                    try {
                        groups.leave(id, target.id)
                        store.leave(id)
                        onLeft()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        if (e is ApiException && e.code in setOf("GROUP_NOT_FOUND", "NOT_GROUP_MEMBER")) {
                            store.leave(id); onLeft()
                        } else if (e is ApiException && e.code in setOf("INVALID_SUCCESSOR", "SUCCESSOR_REQUIRED")) {
                            selected = null
                            try {
                                store.applyServerGroup(groups.detail(id, session.account.userId))
                                if (store.group(id)?.members?.count { !it.isMe } == 0) {
                                    toast.show("다른 멤버가 없어졌어요. 그룹 정보를 다시 확인해 주세요")
                                    delay(1200)
                                    onBack()
                                } else toast.show(e.toUserMessage())
                            } catch (refresh: Exception) {
                                if (refresh is CancellationException) throw refresh
                                toast.show(refresh.toUserMessage())
                            }
                        } else toast.show(e.toUserMessage())
                    } finally { leaving = false }
                }
            }
        })
}
