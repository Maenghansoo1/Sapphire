package com.sapphire.hawe.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.model.MissionSearchResult
import com.sapphire.hawe.core.net.CalendarRepository
import com.sapphire.hawe.core.net.SearchRepository
import com.sapphire.hawe.designsystem.components.HButton
import com.sapphire.hawe.designsystem.components.HChip
import com.sapphire.hawe.designsystem.components.HNavBar
import com.sapphire.hawe.designsystem.components.HNavLeading
import com.sapphire.hawe.designsystem.components.HTextField
import com.sapphire.hawe.designsystem.components.HToastHost
import com.sapphire.hawe.designsystem.components.rememberHToastState
import com.sapphire.hawe.designsystem.layout.ctaBottomPadding
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HLayout
import com.sapphire.hawe.designsystem.tokens.HRadius
import com.sapphire.hawe.designsystem.tokens.HType
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 지난 미션 찾기(TODO_2/B3). 낱말로 내가 볼 수 있는 지난 미션을 찾고, AI 가 넓혀 찾은 항목에는 이유를 붙여요.
 * 내가 올린 미션을 누르면 기존 결과 화면으로 가요(그 달 달력 기록을 먼저 받아 와서 결과 화면이 미션을 알게 해요).
 * 임시 UI: Figma 확정 시 교체. 검색어는 저장하지 않아요.
 */
@Composable
fun SearchScreen(repository: SearchRepository, calendar: CalendarRepository, store: DataStore, onBack: () -> Unit,
    onOpen: (groupId: Long, missionId: Long) -> Unit) {
    val scope = rememberCoroutineScope()
    val controller = remember(repository, scope) {
        MissionSearchController(scope, { query, groupId -> repository.searchMissions(query, groupId) })
    }
    val toast = rememberHToastState()
    val today = remember { LocalDate.now() }
    var opening by remember { mutableStateOf(false) }
    val groups = store.groups

    fun open(result: MissionSearchResult) {
        if (opening) return
        searchOpenBlock(result, store.group(result.groupId) != null)?.let { toast.show(it); return }
        opening = true
        scope.launch {
            try {
                // 결과 화면은 현재 미션이나 달력 기록으로 미션을 알아봐요. 검색으로 연 지난 미션은 그 달 기록을 먼저 받아 와요
                val known = store.todayMission(result.groupId, result.missionId) != null ||
                    store.calendarRecord(result.groupId, result.missionId) != null
                if (!known) {
                    val month = YearMonth.from(result.missionDate)
                    store.applyCalendar(result.groupId, month, calendar.month(result.groupId, month))
                }
                onOpen(result.groupId, result.missionId)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                toast.show("잠시 후 다시 시도해 주세요")
            } finally {
                opening = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(HColors.bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = HLayout.screenPaddingCompact)) {
            HNavBar("지난 미션 찾기", leading = HNavLeading.BACK, onLeading = onBack)
            HTextField(controller.query, controller::onQueryChange, placeholder = "예: 고양이, 비 오는 날",
                maxLength = SEARCH_MAX_LENGTH, imeAction = ImeAction.Search, onImeAction = controller::onSubmit)
            if (groups.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HChip("전체", controller.groupId == null) { controller.selectGroup(null) }
                groups.forEach { group -> HChip(group.detail.name, controller.groupId == group.id) { controller.selectGroup(group.id) } }
            }
            Spacer(Modifier.height(12.dp))
            when (val state = controller.state) {
                SearchState.Idle -> Message("지난 미션을 낱말로 찾아요", "AI가 비슷한 낱말까지 넓혀서 찾아 줄 때도 있어요")
                SearchState.TooShort -> Message("두 글자 이상 입력해 주세요", null)
                SearchState.TooLong -> Message("${SEARCH_MAX_LENGTH}자 이하로 입력해 주세요", null)
                is SearchState.Loading -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = HColors.accent, strokeWidth = 3.dp)
                }
                is SearchState.Empty -> Message("관련 미션이 없어요", "다른 낱말로 찾아볼까요?")
                is SearchState.Failed -> Column(Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(if (state.offline) "네트워크 연결을 확인해 주세요" else "검색하지 못했어요",
                        style = HType.plex(15, FontWeight.SemiBold), color = HColors.ink)
                    HButton("다시 시도", height = 48.dp, onClick = controller::retry)
                }
                is SearchState.Loaded -> LazyColumn(Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = ctaBottomPadding(24.dp)), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.results, key = { it.missionId }) { result -> ResultRow(result, today, enabled = !opening) { open(result) } }
                }
            }
        }
        HToastHost(toast)
    }
}

@Composable
private fun Message(title: String, detail: String?) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = HType.plex(15, FontWeight.SemiBold), color = HColors.ink, textAlign = TextAlign.Center)
        detail?.let { Text(it, style = HType.caption13, color = HColors.textSecondary, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun ResultRow(result: MissionSearchResult, today: LocalDate, enabled: Boolean, onClick: () -> Unit) {
    val description = searchItemDescription(result, today)
    Row(Modifier.fillMaxWidth().background(HColors.surface, RoundedCornerShape(HRadius.card))
        .clickable(enabled = enabled, onClick = onClick)
        .clearAndSetSemantics { contentDescription = description; role = Role.Button }
        .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(result.content, style = HType.plex(15, FontWeight.SemiBold), color = HColors.ink,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(result.groupName.ifBlank { null }, result.type?.displayName,
                searchDateText(result.missionDate, today)).joinToString(" · "),
                style = HType.caption12, color = HColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            searchReason(result)?.let { Text(it, style = HType.caption12, color = HColors.accentPressed, maxLines = 1) }
        }
        Text(if (result.submitted) "올림" else "안 올림",
            Modifier.border(1.dp, if (result.submitted) HColors.accent else HColors.border, CircleShape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            style = HType.caption12, color = if (result.submitted) HColors.accentPressed else HColors.textTertiary, maxLines = 1)
    }
}
