package com.sapphire.hawe.feature.groups

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.AppLinks
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.model.GroupDetail
import com.sapphire.hawe.core.model.GroupMember
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.layout.ctaBottomPadding
import com.sapphire.hawe.designsystem.tokens.*
import java.time.LocalTime
import java.util.UUID
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.COMING_SOON
import com.sapphire.hawe.core.net.ApiException
import com.sapphire.hawe.core.net.GroupRepository
import com.sapphire.hawe.core.net.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun GroupInfoScreen(id: Long, store: DataStore, groups: GroupRepository, session: AppSession,
    onBack: () -> Unit, onProfile: () -> Unit, onLeft: () -> Unit) {
    LaunchedEffect(id) {
        try { store.applyServerGroup(groups.detail(id, session.account.userId)) }
        catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is ApiException && (e.status == 403 || e.status == 404)) {
                store.leave(id); onLeft()
            }
        }
    }
    val group = store.group(id)
    if (group == null) {
        Box(Modifier.fillMaxSize().background(HColors.bg))
        return
    }
    var showLeave by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val toast = rememberHToastState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val bottom = ctaBottomPadding()
    Box(Modifier.fillMaxSize().background(HColors.bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = HLayout.screenPaddingCompact)) {
            HNavBar("그룹 정보", leading = HNavLeading.BACK, onLeading = onBack)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottom + 8.dp)) {
                Column(Modifier.padding(top = 12.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(group.name, style = HType.plex(24, FontWeight.Bold), color = HColors.ink)
                    Text("${group.relationship.displayName} · ${dotDate(group.createdAt)}부터 · 미션 ${group.missionCount}회",
                        style = HType.plex(14), color = HColors.textSecondary)
                }
                Column(Modifier.fillMaxWidth().background(HColors.surface, RoundedCornerShape(HRadius.card)).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("초대 코드", style = HType.plex(13, FontWeight.SemiBold), color = HColors.textSecondary)
                            SelectionContainer {
                                Text(group.inviteCode, style = HType.plex(26, FontWeight.Bold)
                                    .copy(letterSpacing = androidx.compose.ui.unit.TextUnit(3.12f, androidx.compose.ui.unit.TextUnitType.Sp)),
                                    color = HColors.ink)
                            }
                        }
                        Box(Modifier.size(HLayout.minTouch).background(HColors.bg, CircleShape)
                            .border(1.dp, HColors.border, CircleShape).clickable {
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(ClipData.newPlainText("초대 코드", group.inviteCode))
                                if (Build.VERSION.SDK_INT < 33) toast.show("초대 코드를 복사했어요")
                            }.semantics { contentDescription = "초대 코드 복사" }, contentAlignment = Alignment.Center) {
                            HIconView(HIcon.COPY, size = 20.dp)
                        }
                    }
                    HButton("초대 링크 공유하기") { shareInvite(context, group) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("멤버", style = HType.sectionTitle, color = HColors.ink)
                    Spacer(Modifier.weight(1f))
                    Text("${group.members.size} / ${group.relationship.maxMembers}명", style = HType.caption13,
                        color = HColors.textSecondary)
                }
                group.members.forEach { member ->
                    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HAvatar(member.displayed.avatar, size = 36.dp)
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(member.displayed.nickname, style = HType.plex(15, FontWeight.Medium), color = HColors.ink,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            memberBadge(member)?.let {
                                Text(it, style = HType.caption12, color = HColors.textSecondary, maxLines = 1)
                            }
                        }
                        if (member.isMe) Box(Modifier.height(34.dp).border(1.dp, HColors.border, CircleShape)
                            .clickable { toast.show(COMING_SOON) }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text("내 프로필 편집", style = HType.plex(13, FontWeight.SemiBold), color = HColors.ink)
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(HColors.line))
                    HListRow("주제 도착 시간", value = topicTimeText(group.topicTime),
                        showsChevron = false) { toast.show(COMING_SOON) }
                    HListRow("알림", value = "준비 중") { toast.show(COMING_SOON) }
                    Box(Modifier.fillMaxWidth().height(52.dp).clickable {
                        if (group.amIOwner && group.members.size > 1) toast.show(
                            "그룹장은 다른 멤버에게 그룹장을 넘긴 뒤 나갈 수 있어요. 지금은 준비 중이에요")
                        else showLeave = true
                    },
                        contentAlignment = Alignment.CenterStart) {
                        Text("그룹 나가기", style = HType.body15, color = HColors.danger)
                    }
                }
            }
        }
        HToastHost(toast)
    }
    if (showLeave) HAlert("그룹에서 나갈까요?", leaveMessage(group), "나가기",
        onCancel = { showLeave = false }, onConfirm = {
            if (!leaving) {
                leaving = true; showLeave = false
                scope.launch {
                    try { groups.leave(id); store.leave(id); onLeft() }
                    catch (e: Exception) {
                        if (e is CancellationException) throw e
                        if (e is ApiException && (e.code == "GROUP_NOT_FOUND" || e.code == "NOT_GROUP_MEMBER")) {
                            store.leave(id); onLeft()
                        } else toast.show(e.toUserMessage())
                        leaving = false
                    }
                }
            }
        })
}

private fun shareInvite(context: Context, group: GroupDetail) {
    val body = "HA'WE '${group.name}' 그룹에 초대할게요. 초대 코드: ${group.inviteCode}\n${AppLinks.invite(group.inviteCode)}"
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, body) }
    context.startActivity(Intent.createChooser(intent, "초대 링크 공유하기"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicTimeSheet(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    var hour by remember { mutableIntStateOf(((initial.hour + 11) % 12)) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    var amPm by remember { mutableIntStateOf(if (initial.hour < 12) 0 else 1) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HColors.bg) {
        Column(Modifier.fillMaxWidth().height(310.dp).padding(horizontal = HLayout.screenPaddingCompact),
            horizontalAlignment = Alignment.CenterHorizontally) {
            HNavBar(title = "주제 도착 시간", trailing = {
                Text("완료", Modifier.clickable { onConfirm(LocalTime.of((hour + 1) % 12 + amPm * 12, minute)) },
                    style = HType.plex(16, FontWeight.SemiBold), color = HColors.ink)
            })
            Text("매일 이 시간에 그룹의 새 미션이 열려요.", Modifier.padding(top = 4.dp),
                style = HType.caption13, color = HColors.textSecondary)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                WheelPicker(listOf("오전", "오후"), amPm, { amPm = it }, Modifier.weight(1f))
                WheelPicker((1..12).map(Int::toString), hour, { hour = it }, Modifier.weight(1f))
                WheelPicker((0..59).map { it.toString().padStart(2, '0') }, minute,
                    { minute = it }, Modifier.weight(1f))
            }
        }
    }
}
