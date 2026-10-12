package com.sapphire.hawe.feature.groups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.HaweTime
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.net.GroupRepository
import com.sapphire.hawe.core.net.toUserMessage
import com.sapphire.hawe.core.model.AvatarContent
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.assets.ProfileCharacter
import com.sapphire.hawe.designsystem.assets.Relationship
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.layout.HCtaScaffold
import com.sapphire.hawe.designsystem.tokens.*
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateGroupScreen(groups: GroupRepository, store: DataStore, session: AppSession,
    onBack: () -> Unit, onCreated: (Long) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var relationship by rememberSaveable { mutableStateOf(Relationship.FRIEND) }
    var selectedDate by rememberSaveable { mutableStateOf(Instant.now().atZone(HaweTime.SEOUL).toLocalDate()) }
    var showDate by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val toast = rememberHToastState()
    val state = CreateGroupState(name, relationship, selectedDate)
    Box(Modifier.fillMaxSize().background(HColors.bg)) { Column {
        Box(Modifier.padding(horizontal = HLayout.screenPadding)) {
            HNavBar(leading = HNavLeading.BACK, onLeading = onBack)
        }
        HCtaScaffold(bottomInset = 40.dp, content = {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("새 그룹 만들기", style = HType.screenTitle, color = HColors.ink)
                    Text(state.subtitle, style = HType.body15, color = HColors.textSecondary)
                }
                HTextField(name, { name = it }, "그룹 이름을 입력해 주세요", label = "그룹 이름",
                    maxLength = 16, textSize = 17, labelSpacing = 10.dp)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("어떤 사이인가요?", style = HType.fieldLabel, color = HColors.textSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Relationship.entries.forEach { option ->
                            HChip(option.displayName, option == relationship) { relationship = option }
                        }
                    }
                    Row(Modifier.fillMaxWidth().border(1.dp, HColors.line, RoundedCornerShape(HRadius.button))
                        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("홈 화면에서 이렇게 보여요", style = HType.plex(13, FontWeight.SemiBold), color = HColors.ink)
                            Text("캐릭터 사이에 ${relationship.decorPhrase} 떠요", style = HType.caption12,
                                color = HColors.textSecondary)
                        }
                        Spacer(Modifier.weight(1f))
                        MemberRow(listOf(AvatarContent.Character(ProfileCharacter.MONGSIL),
                            AvatarContent.Character(ProfileCharacter.GOMDOL)), relationship, avatarSize = 38.dp)
                    }
                }
                AnimatedVisibility(state.showsStartDate) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("우리의 1일", style = HType.fieldLabel, color = HColors.textSecondary)
                        Row(Modifier.fillMaxWidth().height(54.dp)
                            .border(1.dp, HColors.border, RoundedCornerShape(HRadius.input))
                            .clickable { showDate = true }.semantics { contentDescription = "우리의 1일 선택" }
                            .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(dotDate(selectedDate), style = HType.plex(17), color = HColors.ink)
                            Spacer(Modifier.weight(1f))
                            HIconView(HIcon.CALENDAR, size = 20.dp)
                        }
                        Text("이 날부터 함께한 날을 세고, 100일·1주년 같은 기념일엔 특별한 주제가 와요.",
                            style = HType.plex(13, lineHeightMultiple = 1.5f), color = HColors.textSecondary)
                    }
                }
                Row(Modifier.fillMaxWidth().background(HColors.surface, RoundedCornerShape(HRadius.button))
                    .padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("최대 인원", style = HType.plex(15, FontWeight.SemiBold), color = HColors.ink)
                        Text(if (state.showsStartDate) "연인 그룹은 둘만 함께해요" else "나를 포함해 2~${state.maxMembers}명",
                            style = HType.caption13, color = HColors.textSecondary)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${state.maxMembers}명", style = HType.plex(17, FontWeight.Bold), color = HColors.ink)
                }
            }
        }, cta = {
            HButton("그룹 만들고 초대 코드 받기", enabled = state.canCreate && !submitted) {
                if (!submitted) {
                    submitted = true
                    scope.launch {
                        try {
                            val detail = groups.create(name.trim(), relationship, state.startDateForCreation,
                                session.account.userId, session.account.profile)
                            store.applyServerGroup(detail)
                            onCreated(detail.id)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            toast.show(e.toUserMessage())
                            submitted = false
                        }
                    }
                }
            }
        })
    }; HToastHost(toast) }
    if (showDate) StartDateSheet(selectedDate, onDismiss = { showDate = false }, onConfirm = {
        selectedDate = it; showDate = false
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartDateSheet(date: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val today = remember { Instant.now().atZone(HaweTime.SEOUL).toLocalDate() }
    val picker = rememberDatePickerState(initialSelectedDateMillis = datePickerMillis(date),
        selectableDates = remember(today) { object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = isSelectableDate(utcTimeMillis, today)
            override fun isSelectableYear(year: Int): Boolean = year <= today.year
        } })
    val currentConfiguration = LocalConfiguration.current
    val koreanConfiguration = remember(currentConfiguration) {
        android.content.res.Configuration(currentConfiguration).apply { setLocale(Locale.KOREAN) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HColors.bg) {
        Column(Modifier.fillMaxWidth().height(450.dp).padding(horizontal = HLayout.screenPaddingCompact)) {
            HNavBar(title = "우리의 1일", trailing = {
                Text("완료", Modifier.clickable {
                    picker.selectedDateMillis?.let { onConfirm(dateFromPickerMillis(it)) } ?: onDismiss()
                }, style = HType.plex(16, FontWeight.SemiBold), color = HColors.ink)
            })
            CompositionLocalProvider(LocalConfiguration provides koreanConfiguration) {
                DatePicker(state = picker, colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = HColors.ink, selectedDayContentColor = HColors.bg,
                    todayContentColor = HColors.ink, todayDateBorderColor = HColors.ink))
            }
        }
    }
}
