package com.sapphire.hawe.feature.groups

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sapphire.hawe.app.LocalStore
import com.sapphire.hawe.data.DataStore
import com.sapphire.hawe.designsystem.CharacterPicker
import com.sapphire.hawe.designsystem.HAlert
import com.sapphire.hawe.designsystem.HAvatar
import com.sapphire.hawe.designsystem.HButton
import com.sapphire.hawe.designsystem.HCTAScaffold
import com.sapphire.hawe.designsystem.HChip
import com.sapphire.hawe.designsystem.HColors
import com.sapphire.hawe.designsystem.HDivider
import com.sapphire.hawe.designsystem.HIcon
import com.sapphire.hawe.designsystem.HIconView
import com.sapphire.hawe.designsystem.HLayout
import com.sapphire.hawe.designsystem.HListRow
import com.sapphire.hawe.designsystem.HNavBar
import com.sapphire.hawe.designsystem.HPillButton
import com.sapphire.hawe.designsystem.HRadius
import com.sapphire.hawe.designsystem.HScreen
import com.sapphire.hawe.designsystem.HTextField
import com.sapphire.hawe.designsystem.HToast
import com.sapphire.hawe.designsystem.HToggle
import com.sapphire.hawe.designsystem.HType
import com.sapphire.hawe.designsystem.MemberRow
import com.sapphire.hawe.designsystem.NavLeading
import com.sapphire.hawe.designsystem.ProfilePreviewRow
import com.sapphire.hawe.designsystem.plainClickable
import com.sapphire.hawe.designsystem.plex
import com.sapphire.hawe.model.AvatarContent
import com.sapphire.hawe.model.GroupInvitePreview
import com.sapphire.hawe.model.GroupMember
import com.sapphire.hawe.model.GroupSummary
import com.sapphire.hawe.model.Profile
import com.sapphire.hawe.model.ProfileCharacter
import com.sapphire.hawe.model.Relationship
import com.sapphire.hawe.model.SEOUL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val koDate = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN)
private val koTime = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

/** 초대 링크 (Universal Link / App Link). TODO: 실제 도메인을 정하면 바꿔요 (iOS 와 같은 임시 주소). */
fun inviteLink(code: String) = "https://hawe.app/invite/$code"

// MARK: - 04 홈 · 내 그룹 목록 (Figma 1:461)

@Composable
fun HomeScreen(onAdd: () -> Unit, onMyPage: () -> Unit, onOpenGroup: (String) -> Unit) {
    val store = LocalStore.current
    // 하루가 지나 새 미션이 열렸으면 만들어 둬요.
    LaunchedEffect(Unit) { store.refreshTodayMissions() }
    val groups = store.summaries
    val today = LocalDate.now(SEOUL).format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN))

    HScreen {
        Row(
            Modifier.fillMaxWidth().height(HLayout.headerHeight).padding(horizontal = HLayout.screenPaddingCompact),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("내 그룹", style = HType.screenTitle, color = HColors.ink)
            Spacer(Modifier.weight(1f))
            // 아이콘은 Figma 크기·간격(16) 그대로, 눌리는 영역만 44 로 넓혀요.
            Row(Modifier.offset(x = 11.dp), horizontalArrangement = Arrangement.spacedBy((-5).dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(HLayout.minTouch).plainClickable(onClick = onAdd), contentAlignment = Alignment.Center) { HIconView(HIcon.Plus, 24.dp) }
                Box(Modifier.size(HLayout.minTouch).plainClickable(onClick = onMyPage), contentAlignment = Alignment.Center) { HIconView(HIcon.Person, 22.dp) }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HLayout.screenPaddingCompact)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text("$today · 그룹 ${groups.size}개", style = plex(14), color = HColors.textSecondary, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                groups.forEach { GroupCard(it) { onOpenGroup(it.id) } }
            }
        }
    }
}

/** 홈 그룹 카드 — 테두리 line · 모서리 20 · 안쪽 18 · 간격 14 */
@Composable
private fun GroupCard(group: GroupSummary, onClick: () -> Unit) {
    val shape = RoundedCornerShape(HRadius.card)
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, HColors.line, shape)
            .plainClickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(group.name, style = plex(18, FontWeight.Bold), color = HColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val parts = listOfNotNull(group.relationship.displayName, "${group.members.size}명", group.daysTogether()?.let { "함께한 지 ${it}일" })
            Text(parts.joinToString(" · "), style = HType.caption13, color = HColors.textSecondary)
        }
        MemberRow(group.members, group.relationship)
        Column(
            Modifier.fillMaxWidth().background(HColors.surface, RoundedCornerShape(HRadius.input)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("오늘의 미션 · ${group.missionType.displayName}", style = plex(12, FontWeight.SemiBold), color = HColors.textSecondary)
            Text(group.topic, style = plex(15, FontWeight.SemiBold), color = HColors.ink)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${group.submittedCount} / ${group.members.size}명 완료", style = HType.caption13, color = HColors.textSecondary)
            Spacer(Modifier.weight(1f))
            // 내 미션 남음 = accent / 열어보기 = surface
            val remaining = !group.didSubmit
            Box(
                Modifier.height(28.dp).background(if (remaining) HColors.accent else HColors.surface, CircleShape).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (remaining) "내 미션 남음" else "열어보기", style = plex(13, FontWeight.SemiBold), color = if (remaining) HColors.onAccent else HColors.ink)
            }
        }
    }
}

// MARK: - 02 그룹 만들기 / 02-1 연인 선택 (Figma 1:338 / 1:375)

/** 그룹 이름(최대 16자), 관계 5종, 관계별 장식 미리보기. 연인을 고르면 '우리의 1일' 이 나타나고 최대 2명. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(onBack: () -> Unit, onCreated: (String, Relationship, LocalDate?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    // Figma 02 기본 화면이 '친구' 선택 상태예요.
    var relationship by rememberSaveable { mutableStateOf(Relationship.Friend) }
    var startDate by remember { mutableStateOf(LocalDate.now(SEOUL)) }
    var picking by remember { mutableStateOf(false) }

    HScreen {
        HNavBar(modifier = Modifier.padding(horizontal = HLayout.screenPadding), leading = NavLeading.Back(onBack))
        HCTAScaffold(
            bottomInset = 40.dp,
            content = {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("새 그룹 만들기", style = HType.screenTitle, color = HColors.ink)
                        Text(
                            if (relationship == Relationship.Lover) "만든 뒤 초대 코드로 연인 1명을 초대할 수 있어요."
                            else "만든 뒤 초대 코드로 최대 ${relationship.maxMembers - 1}명을 더 초대할 수 있어요.",
                            style = HType.body15, color = HColors.textSecondary,
                        )
                    }
                    HTextField(name, { name = it }, placeholder = "그룹 이름을 입력해 주세요", label = "그룹 이름", maxLength = 16, textSize = 17, labelSpacing = 10.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("어떤 사이인가요?", style = HType.fieldLabel, color = HColors.textSecondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Relationship.entries.forEach { r -> HChip(r.displayName, relationship == r) { relationship = r } }
                        }
                        // 홈 화면 미리보기 — 테두리 line · 모서리 16 · 좌우 16 · 위아래 14 · 아바타 38
                        Row(
                            Modifier.fillMaxWidth().border(1.dp, HColors.line, RoundedCornerShape(HRadius.button)).padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("홈 화면에서 이렇게 보여요", style = plex(13, FontWeight.SemiBold), color = HColors.ink)
                                Text("캐릭터 사이에 ${relationship.decorPhrase} 떠요", style = HType.caption12, color = HColors.textSecondary)
                            }
                            MemberRow(
                                listOf(AvatarContent.Character(ProfileCharacter.Mongsil), AvatarContent.Character(ProfileCharacter.Gomdol)),
                                relationship, avatarSize = 38.dp,
                            )
                        }
                    }
                    if (relationship == Relationship.Lover) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("우리의 1일", style = HType.fieldLabel, color = HColors.textSecondary)
                            Row(
                                Modifier.fillMaxWidth().height(54.dp).border(1.dp, HColors.border, RoundedCornerShape(HRadius.input))
                                    .plainClickable { picking = true }.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(startDate.format(koDate), style = plex(17), color = HColors.ink, modifier = Modifier.weight(1f))
                                HIconView(HIcon.Calendar, 20.dp)
                            }
                            Text("이 날부터 함께한 날을 세고, 100일·1주년 같은 기념일엔 특별한 주제가 와요.", style = plex(13, lineHeight = 1.5f), color = HColors.textSecondary)
                        }
                    }
                    // 최대 인원 — surface · 모서리 16 · 좌우 18 · 위아래 16
                    Row(
                        Modifier.fillMaxWidth().background(HColors.surface, RoundedCornerShape(HRadius.button)).padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("최대 인원", style = plex(15, FontWeight.SemiBold), color = HColors.ink)
                            Text(
                                if (relationship == Relationship.Lover) "연인 그룹은 둘만 함께해요" else "나를 포함해 2~${relationship.maxMembers}명",
                                style = HType.caption13, color = HColors.textSecondary,
                            )
                        }
                        Text("${relationship.maxMembers}명", style = plex(17, FontWeight.Bold), color = HColors.ink)
                    }
                }
            },
            cta = {
                HButton("그룹 만들고 초대 코드 받기", enabled = name.isNotBlank()) {
                    onCreated(name.trim(), relationship, if (relationship == Relationship.Lover) startDate else null)
                }
            },
        )
    }

    if (picking) {
        // '우리의 1일' — 오늘 이후 날짜는 고를 수 없어요.
        val todayMillis = LocalDate.now(SEOUL).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                Text("완료", style = plex(16, FontWeight.SemiBold), color = HColors.ink, modifier = Modifier.padding(16.dp).plainClickable {
                    state.selectedDateMillis?.let { startDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                })
            },
            colors = DatePickerDefaults.colors(containerColor = HColors.bg),
        ) {
            DatePicker(
                state,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = HColors.bg,
                    selectedDayContainerColor = HColors.ink,
                    todayDateBorderColor = HColors.ink,
                    todayContentColor = HColors.ink,
                ),
            )
        }
    }
}

// MARK: - 03 초대 코드로 참여 (Figma 1:418)

/** 6자리 코드(영문 대문자+숫자). 6자리가 채워지면 그룹 미리보기, 정원 초과·잘못된 코드는 오류 표시. */
@Composable
fun JoinGroupScreen(initialCode: String, onBack: () -> Unit, onJoined: (String) -> Unit) {
    val store = LocalStore.current
    // 입력 중인 글자를 바로 대문자로 바꾸면 키보드 조합이 꼬여서 글자가 빠져요. 입력은 그대로 두고 보여줄 때·비교할 때만 대문자로 써요.
    var input by rememberSaveable { mutableStateOf(sanitize(initialCode)) }
    val code = input.uppercase()
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (code.length < 6) focus.requestFocus() }

    val lookup = if (code.length == 6) store.lookUpInvite(code) else null
    val preview = lookup?.getOrNull()
    val error = lookup?.let { DataStore.inviteError(it) }

    HScreen {
        HNavBar(modifier = Modifier.padding(horizontal = HLayout.screenPadding), leading = NavLeading.Back(onBack))
        HCTAScaffold(
            bottomInset = 40.dp,
            content = {
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("초대 코드 입력", style = HType.screenTitle, color = HColors.ink)
                        Text("친구에게 받은 6자리 코드를 입력해 주세요.", style = HType.body15, color = HColors.textSecondary)
                    }
                    // 칸 6개 · 간격 8 · 높이 58 · 모서리 14. 채운 칸 surface, 지금 칸은 흰 바탕 + ink 테두리 1.5
                    BasicTextField(
                        value = input,
                        onValueChange = { input = sanitize(it) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                        modifier = Modifier.focusRequester(focus).onFocusChanged { focused = it.isFocused },
                        decorationBox = { inner ->
                            Box {
                                Box(Modifier.alpha(0f).size(1.dp)) { inner() }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    repeat(6) { index ->
                                        val current = focused && index == code.length
                                        val shape = RoundedCornerShape(HRadius.input)
                                        Box(
                                            Modifier.weight(1f).height(58.dp)
                                                .background(if (current) HColors.bg else HColors.surface, shape)
                                                .then(if (current) Modifier.border(1.5.dp, HColors.ink, shape) else Modifier),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (index < code.length) Text(code[index].toString(), style = plex(24, FontWeight.Bold), color = HColors.ink)
                                        }
                                    }
                                }
                            }
                        },
                    )
                    if (error != null) Text(error.message, style = HType.caption13, color = HColors.danger)
                    else Text("링크로 받았다면 링크를 누르면 자동으로 입력돼요.", style = HType.caption13, color = HColors.textSecondary)
                    if (preview != null) InviteCard(preview)
                }
            },
            cta = { HButton("참여하기", enabled = preview != null) { onJoined(code) } },
        )
    }
}

/** 영문·숫자만, 6자리까지 (대소문자는 그대로) */
private fun sanitize(text: String) = text.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' }.take(6)

/** 참여할 그룹 미리보기 — 테두리 line · 모서리 18 · 안쪽 18 · 아바타 32 */
@Composable
private fun InviteCard(preview: GroupInvitePreview) {
    Column(
        Modifier.fillMaxWidth().border(1.dp, HColors.line, RoundedCornerShape(18.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("참여할 그룹", style = plex(13, FontWeight.SemiBold), color = HColors.textSecondary)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(preview.name, style = plex(18, FontWeight.Bold), color = HColors.ink, maxLines = 1)
            Text("${preview.relationship.displayName} · ${preview.members.size} / ${preview.relationship.maxMembers}명 참여 중", style = plex(14), color = HColors.textSecondary)
        }
        MemberRow(preview.members, preview.relationship, avatarSize = 32.dp)
    }
}

// MARK: - 16 그룹 정보 · 설정 (Figma 1:1454)

/** 초대 코드 복사·링크 공유, 멤버 목록(내 줄에 '내 프로필 편집'), 주제 도착 시간(방장만 변경), 그룹 알림, 그룹 나가기 */
@Composable
fun GroupInfoScreen(groupId: String, onBack: () -> Unit, onEditProfile: () -> Unit, onLeft: () -> Unit) {
    val store = LocalStore.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val group = store.group(groupId) ?: return
    var toast by remember { mutableStateOf<String?>(null) }
    var pickingTime by remember { mutableStateOf(false) }
    var confirmsLeave by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        HScreen {
            HNavBar("그룹 정보", modifier = Modifier.padding(horizontal = HLayout.screenPaddingCompact), leading = NavLeading.Back(onBack))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = HLayout.screenPaddingCompact).navigationBarsPadding().padding(bottom = 32.dp),
            ) {
                Column(Modifier.padding(top = 12.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(group.name, style = plex(24, FontWeight.Bold), color = HColors.ink)
                    Text("${group.relationship.displayName} · ${group.createdAt.format(koDate)}부터 · 미션 ${group.missionCount}회", style = plex(14), color = HColors.textSecondary)
                }
                // 초대 코드 카드 — surface · 모서리 20 · 안쪽 18 · 간격 14
                Column(
                    Modifier.fillMaxWidth().background(HColors.surface, RoundedCornerShape(HRadius.card)).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("초대 코드", style = plex(13, FontWeight.SemiBold), color = HColors.textSecondary)
                            Text(group.inviteCode, style = plex(26, FontWeight.Bold).copy(letterSpacing = 3.12.sp), color = HColors.ink)
                        }
                        Box(
                            Modifier.size(HLayout.minTouch).background(HColors.bg, CircleShape).border(1.dp, HColors.border, CircleShape)
                                .plainClickable {
                                    clipboard.setText(AnnotatedString(group.inviteCode))
                                    toast = "초대 코드를 복사했어요"
                                },
                            contentAlignment = Alignment.Center,
                        ) { HIconView(HIcon.Copy, 20.dp) }
                    }
                    HButton("초대 링크 공유하기") {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "HA'WE '${group.name}' 그룹에 초대할게요. 초대 코드: ${group.inviteCode}\n${inviteLink(group.inviteCode)}")
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    }
                }
                Row(Modifier.padding(top = 24.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("멤버", style = HType.sectionTitle, color = HColors.ink, modifier = Modifier.weight(1f))
                    Text("${group.members.size} / ${group.relationship.maxMembers}명", style = HType.caption13, color = HColors.textSecondary)
                }
                group.members.forEach { member -> MemberLine(member, onEditProfile) }
                // 위 구분선 + 위 12 · 목록 줄 52
                HDivider()
                Column(Modifier.padding(top = 12.dp)) {
                    // 주제 도착 시간은 방장만 바꿀 수 있어요 — 방장이 아니면 꺾쇠 없이 값만 보여요.
                    HListRow("주제 도착 시간", value = group.topicTime.format(koTime), showsChevron = group.amIOwner, enabled = group.amIOwner) { pickingTime = true }
                    // Figma 에 그룹 알림 설정 화면이 없어서, 누르면 켜짐 ↔ 꺼짐이 바로 바뀌어요.
                    HListRow("알림", value = if (group.notify) "켜짐" else "꺼짐") {
                        val on = !group.notify
                        store.setNotify(on, groupId)
                        toast = if (on) "이 그룹 알림을 켰어요" else "이 그룹 알림을 껐어요"
                    }
                    Text(
                        "그룹 나가기", style = HType.body15, color = HColors.danger,
                        modifier = Modifier.fillMaxWidth().height(52.dp).plainClickable { confirmsLeave = true }.padding(top = 15.dp),
                    )
                }
            }
        }
        HToast(toast, onDismiss = { toast = null })
        if (confirmsLeave) {
            // 나간 뒤에도 내가 올린 작품·댓글은 그룹에 남아요 (사용자 결정 2026-10-12).
            var message = "'${group.name}'에서 나가도 내가 올린 작품과 댓글은 그룹에 남아요. 다시 들어오려면 초대 코드가 필요해요."
            if (group.amIOwner && group.members.size > 1) message += "\n방장은 가장 먼저 들어온 멤버에게 넘어가요."
            HAlert("그룹에서 나갈까요?", message, "나가기", onCancel = { confirmsLeave = false }, onConfirm = { confirmsLeave = false; onLeft() })
        }
    }

    if (pickingTime) TopicTimeDialog(group.topicTime, onDismiss = { pickingTime = false }) { store.setTopicTime(it, groupId) }
}

/** 멤버 줄 — 높이 52 · 아바타 36 · 간격 12, "나 · 방장" 표시 */
@Composable
private fun MemberLine(member: GroupMember, onEditProfile: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        HAvatar(member.displayed.avatar, 36.dp)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(member.displayed.nickname, style = plex(15, FontWeight.Medium), color = HColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            val badge = listOfNotNull(if (member.isMe) "나" else null, if (member.isOwner) "방장" else null).joinToString(" · ")
            if (badge.isNotEmpty()) Text(badge, style = HType.caption12, color = HColors.textSecondary)
        }
        if (member.isMe) HPillButton("내 프로필 편집", onClick = onEditProfile)
    }
}

/** 주제 도착 시간 고르기 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicTimeDialog(initial: LocalTime, onDismiss: () -> Unit, onDone: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = false)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.background(HColors.bg, RoundedCornerShape(HRadius.largeCard)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("주제 도착 시간", style = HType.navTitle, color = HColors.ink)
            Text("매일 이 시간에 그룹의 새 미션이 열려요.", style = HType.caption13, color = HColors.textSecondary)
            TimePicker(
                state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = HColors.surface,
                    selectorColor = HColors.ink,
                    timeSelectorSelectedContainerColor = HColors.surfaceStrong,
                    timeSelectorSelectedContentColor = HColors.ink,
                    periodSelectorSelectedContainerColor = HColors.surfaceStrong,
                    periodSelectorSelectedContentColor = HColors.ink,
                ),
            )
            HButton("완료") {
                onDone(LocalTime.of(state.hour, state.minute))
                onDismiss()
            }
        }
    }
}

// MARK: - 16-1 그룹 내 프로필 (Figma 1:1503)

/** '기본 프로필 사용'을 끄면 이 그룹에서만 쓸 캐릭터/사진·닉네임을 정해요. 다른 그룹에는 영향이 없어요. */
@Composable
fun GroupProfileScreen(groupId: String, onBack: () -> Unit) {
    val store = LocalStore.current
    val group = store.group(groupId) ?: return
    val me = group.me ?: return
    var useDefault by remember { mutableStateOf(me.groupProfile == null) }
    var avatar by remember { mutableStateOf(me.displayed.avatar) }
    var nickname by rememberSaveable { mutableStateOf(me.displayed.nickname) }
    val preview = if (useDefault) me.defaultProfile else Profile(avatar, nickname)
    val canSave = useDefault || nickname.trim().length in 2..10

    HScreen {
        HNavBar("그룹 내 프로필", modifier = Modifier.padding(horizontal = HLayout.screenPadding), leading = NavLeading.Back(onBack))
        HCTAScaffold(
            content = {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("'${group.name}' 그룹에서만 이렇게 보여요. 다른 그룹에는 영향이 없어요.", style = plex(14, lineHeight = 1.5f), color = HColors.textSecondary)
                    // 기본 프로필 사용 — surface · 모서리 16 · 좌우 16 · 위아래 14
                    Row(
                        Modifier.fillMaxWidth().background(HColors.surface, RoundedCornerShape(HRadius.button)).padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("기본 프로필 사용", style = plex(15, FontWeight.SemiBold), color = HColors.ink)
                            Text("마이페이지의 프로필(${me.defaultProfile.avatar.choiceName} · ${me.defaultProfile.nickname})을 그대로 써요", style = HType.caption12, color = HColors.textSecondary)
                        }
                        Spacer(Modifier.width(12.dp))
                        HToggle(useDefault, { useDefault = it })
                    }
                    ProfilePreviewRow(preview.avatar, "그룹 멤버에게 보이는 모습", preview.nickname, size = 80.dp)
                    if (!useDefault) {
                        CharacterPicker(avatar, { avatar = it }, cellSize = 64.dp, characterSize = 48.dp, rowSpacing = 10.dp)
                        HTextField(nickname, { nickname = it }, placeholder = "닉네임을 입력해 주세요", label = "그룹 내 닉네임", maxLength = 10, textSize = 17)
                    }
                }
            },
            cta = {
                // TODO: 서버 연결 시 group_members 의 *_override 저장
                HButton("저장", enabled = canSave) {
                    store.setMyGroupProfile(if (useDefault) null else Profile(avatar, nickname.trim()), groupId)
                    onBack()
                }
            },
        )
    }
}
