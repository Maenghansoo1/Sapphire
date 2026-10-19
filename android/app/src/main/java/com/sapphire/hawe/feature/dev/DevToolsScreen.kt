package com.sapphire.hawe.feature.dev

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import com.sapphire.hawe.BuildConfig
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.HaweTime
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.dev.AppClock
import com.sapphire.hawe.core.dev.DevFault
import com.sapphire.hawe.core.dev.DevFaults
import com.sapphire.hawe.core.dev.DevMode
import com.sapphire.hawe.core.net.ApiException
import com.sapphire.hawe.core.net.DevClockStatus
import com.sapphire.hawe.core.net.DevRepository
import com.sapphire.hawe.core.net.DevSubmission
import com.sapphire.hawe.core.net.GroupRepository
import com.sapphire.hawe.core.net.MissionRepository
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.tokens.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// 임시 UI: 개발자 전용 조작 화면.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevToolsScreen(session: AppSession, store: DataStore, dev: DevRepository,
    groups: GroupRepository, missions: MissionRepository, onBack: () -> Unit,
    onInsight: (Long) -> Unit, onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = rememberHToastState()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<DevClockStatus?>(null) }
    var groupId by remember { mutableStateOf<Long?>(store.groups.firstOrNull()?.id) }
    val group = store.groups.firstOrNull { it.id == groupId }
    var missionId by remember { mutableStateOf<Long?>(null) }
    val current = groupId?.let(store::todayMissions).orEmpty()
    var memberId by remember { mutableStateOf<Long?>(null) }
    var toMemberId by remember { mutableStateOf<Long?>(null) }
    var selectedSubmission by remember { mutableStateOf<Long?>(null) }
    var submissionRows by remember { mutableStateOf<List<DevSubmission>>(emptyList()) }
    var sticker by remember { mutableStateOf("heart") }
    var comment by remember { mutableStateOf("개발자 테스트 댓글") }
    var template by remember { mutableStateOf<String?>(null) }
    var seedDays by remember { mutableStateOf("30") }
    var seedMembers by remember { mutableStateOf("5") }
    var seedDaily by remember { mutableStateOf("1") }
    var daily by remember { mutableIntStateOf(1) }
    var slotTimes by remember { mutableStateOf(List(5) { "09:00" }) }
    var month by remember { mutableStateOf(YearMonth.from(AppClock.today())) }
    var clockDate by remember { mutableStateOf(AppClock.today()) }
    var clockTime by remember { mutableStateOf(AppClock.now().atZone(HaweTime.SEOUL).toLocalTime()
        .withSecond(0).withNano(0)) }
    var showDate by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var rerollConfirmation by remember { mutableStateOf<Triple<Long, Long, String?>?>(null) }
    var faultRevision by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }

    fun record(message: String) {
        results = (listOf(message) + results).take(20)
        toast.show(message.take(100))
    }
    suspend fun refreshGroups() {
        val loaded = groups.loadAll(session.account.userId)
        store.applyServerGroups(loaded)
    }
    suspend fun refreshMission() {
        groupId?.let { store.applyCurrentMission(it, missions.current(it, session.account.userId)) }
    }
    fun runAction(label: String, block: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            try { record("$label: ${block()}") }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                record("$label 실패: ${if (error is ApiException) error.code + " " else ""}${error.message}")
            } finally { busy = false }
        }
    }
    fun clockResult(value: DevClockStatus) {
        status = value
        DevMode.setOffset(value.offsetSeconds)
        record(value.warning ?: "서버 시각 ${value.serverNow}")
    }
    LaunchedEffect(Unit) {
        try { status = dev.status().also { DevMode.setOffset(it.offsetSeconds) } }
        catch (error: Exception) { if (error is CancellationException) throw error; record("상태 조회 실패: ${error.message}") }
    }
    LaunchedEffect(groupId) {
        missionId = groupId?.let { store.todayMissions(it).firstOrNull()?.id }
        memberId = group?.detail?.members?.firstOrNull()?.id
        toMemberId = group?.detail?.members?.firstOrNull { it.id != memberId }?.id
        submissionRows = emptyList()
        if (groupId != null) try {
            store.applyServerGroup(groups.detail(groupId!!, session.account.userId))
            refreshMission()
        } catch (error: Exception) { if (error is CancellationException) throw error }
    }
    LaunchedEffect(current.map { it.id }) {
        if (current.none { it.id == missionId }) missionId = current.firstOrNull()?.id
    }

    Box(Modifier.fillMaxSize().background(HColors.bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = HLayout.screenPaddingCompact)) {
            HNavBar("개발자 도구", leading = HNavLeading.BACK, onLeading = onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Section("상태") {
                    SelectionContainer { Text("서버 ${BuildConfig.API_BASE_URL} · 유저 ${session.account.profile.nickname}(#${session.account.userId})\n" +
                        "서버시간 ${status?.serverNow ?: "조회 중"} (오프셋 ${status?.offsetSeconds ?: 0}초)\n" +
                        "빌드 ${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}",
                        style = HType.caption13, color = HColors.ink) }
                    Action("새로고침", busy) { runAction("상태") {
                        val next = dev.status(); status = next; DevMode.setOffset(next.offsetSeconds); next.serverNow
                    } }
                }
                Section("서버 시계") {
                    Text("${clockDate} ${clockTime}", style = HType.plex(15), color = HColors.ink)
                    Action("날짜 선택", busy) { showDate = true }
                    Action("시각 선택", busy) {
                        TimePickerDialog(context, { _, h, m -> clockTime = LocalTime.of(h, m) },
                            clockTime.hour, clockTime.minute, true).show()
                    }
                    Action("선택 시각으로", busy) { runAction("시계 설정") {
                        val target = clockDate.atTime(clockTime).atZone(HaweTime.SEOUL).toOffsetDateTime().toString()
                        clockResult(dev.setClock(now = target)); refreshGroups(); refreshMission(); target
                    } }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("+1시간" to 3600L, "+1일" to 86400L, "+7일" to 604800L).forEach { (label, seconds) ->
                            Chip(label, false, !busy, Modifier.weight(1f)) { runAction(label) {
                                clockResult(dev.setClock(plusSeconds = seconds)); refreshGroups(); refreshMission(); "완료"
                            } }
                        }
                    }
                    Action("다음 발행 시각으로", busy, group?.nextPublishAt != null) { runAction("다음 발행") {
                        val seconds = Duration.between(AppClock.now(), group!!.nextPublishAt!!).seconds.coerceAtLeast(0)
                        clockResult(dev.setClock(plusSeconds = seconds)); refreshGroups(); refreshMission(); "완료"
                    } }
                    Action("시계 초기화", busy) { runAction("시계 초기화") {
                        clockResult(dev.resetClock()); refreshGroups(); refreshMission(); "완료"
                    } }
                }
                Section("대상 그룹 / 미션") {
                    FlowChips(store.groups.map { it.id to it.detail.name }, groupId, busy) { groupId = it }
                    FlowChips(current.mapIndexed { i, m -> m.id to "${i + 1} · #${m.id}" }, missionId, busy) {
                        missionId = it; submissionRows = emptyList()
                    }
                }
                Section("미션") {
                    Action("미션 생성", busy, groupId != null) { runAction("미션 생성") {
                        val result = dev.publish(groupId); refreshMission(); "${result.created.size}개 · ${result.message.orEmpty()}"
                    } }
                    Text("틀 선택 (생성·리롤에 적용)", style = HType.caption13, color = HColors.textSecondary)
                    FlowChips(templates.mapIndexed { index, value -> index.toLong() to value },
                        templates.indexOf(template).takeIf { it >= 0 }?.toLong(), busy) {
                        template = templates[it.toInt()]
                    }
                    Action("선택 미션 리롤", busy, groupId != null && missionId != null) {
                        rerollConfirmation = Triple(groupId!!, missionId!!, null)
                    }
                    Action("선택 미션을 ${template ?: "선택한 틀"}로 리롤", busy,
                        groupId != null && missionId != null && template != null) {
                        rerollConfirmation = Triple(groupId!!, missionId!!, template)
                    }
                    Action("선택 틀로 생성", busy, groupId != null && template != null) { runAction("선택 틀 생성") {
                        val result = dev.publish(groupId, template); refreshMission(); "${result.created.size}개"
                    } }
                    Action("선택 미션 마감 당기기", busy, missionId != null) { runAction("마감") {
                        dev.deadlineNow(missionId!!).toString(); refreshMission(); "완료"
                    } }
                    Action("그룹 전체 마감 당기기", busy, groupId != null) { runAction("그룹 마감") {
                        val result = dev.groupDeadlineNow(groupId!!); refreshMission(); result.toString()
                    } }
                }
                Section("대리 행동") {
                    val members = group?.detail?.members.orEmpty()
                    Text("행동 유저", style = HType.caption13, color = HColors.textSecondary)
                    FlowChips(members.map { it.id to it.displayed.nickname }, memberId, busy) { memberId = it }
                    Text("콕 받을 유저", style = HType.caption13, color = HColors.textSecondary)
                    FlowChips(members.map { it.id to it.displayed.nickname }, toMemberId, busy) { toMemberId = it }
                    Action("대리 제출", busy, missionId != null && memberId != null) { runAction("대리 제출") {
                        dev.submitAs(missionId!!, memberId!!); refreshMission(); "완료"
                    } }
                    Action("미제출자 전원 제출", busy, missionId != null) { runAction("전원 제출") {
                        val result = dev.submitMissing(missionId!!); refreshMission(); result.joinToString { "${it.userId}:${it.errorCode ?: "OK"}" }
                    } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("콕" to "poke").forEach { (label, kind) ->
                            Chip(label, false, !busy && groupId != null && memberId != null &&
                                toMemberId != null && memberId != toMemberId, Modifier.weight(1f)) {
                                runAction(label) { dev.poke(groupId!!, memberId!!, toMemberId!!, kind, missionId); "완료" }
                            }
                        }
                    }
                    Action("제출 목록 조회", busy, missionId != null) { runAction("제출 목록") {
                        submissionRows = dev.submissionsOf(missionId!!)
                        selectedSubmission = submissionRows.firstOrNull()?.submissionId
                        "${submissionRows.size}건"
                    } }
                    FlowChips(submissionRows.map { it.submissionId to "${it.nickname} #${it.submissionId}" },
                        selectedSubmission, busy) { selectedSubmission = it }
                    FlowChips(stickers.mapIndexed { index, value -> index.toLong() to value },
                        stickers.indexOf(sticker).toLong(), busy) { sticker = stickers[it.toInt()] }
                    Action("반응", busy, selectedSubmission != null && memberId != null) { runAction("반응") {
                        dev.react(selectedSubmission!!, memberId!!, sticker); "완료"
                    } }
                    HTextField(comment, { comment = it }, "댓글")
                    Action("댓글", busy, selectedSubmission != null && memberId != null && comment.isNotBlank()) {
                        runAction("댓글") { dev.comment(selectedSubmission!!, memberId!!, comment); "완료" }
                    }
                }
                Section("시드") {
                    HTextField(seedDays, { seedDays = it.filter(Char::isDigit) }, "일수 1~90")
                    HTextField(seedMembers, { seedMembers = it.filter(Char::isDigit) }, "멤버 수 2~6")
                    HTextField(seedDaily, { seedDaily = it.filter(Char::isDigit) }, "하루 미션 수 1~5")
                    Action("시드 생성", busy) { runAction("시드 생성") {
                        val seeded = dev.seed(seedDays.toIntOrNull() ?: 30, seedMembers.toIntOrNull() ?: 5,
                            seedDaily.toIntOrNull() ?: 1)
                        refreshGroups(); groupId = seeded.groupId
                        "그룹 #${seeded.groupId}, 미션 ${seeded.missionsCreated}개 · 안 보이면 dev1로 전환하세요"
                    } }
                    Action("[DEV] 그룹 전체 삭제", busy) { confirmReset = true }
                }
                Section("인사이트") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("이전 달", false, !busy, Modifier.weight(1f)) { month = month.minusMonths(1) }
                        Chip(month.toString(), true, false, Modifier.weight(1f)) { }
                        Chip("다음 달", false, !busy, Modifier.weight(1f)) { month = month.plusMonths(1) }
                    }
                    Action("강제 재생성", busy, groupId != null) { runAction("인사이트") {
                        dev.regenerateInsight(groupId!!, month.toString()).toString()
                    } }
                    Action("인사이트 열기", busy, groupId != null) { onInsight(groupId!!) }
                }
                Section("그룹 설정") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("−", false, !busy && daily > 1, Modifier.weight(1f)) { daily-- }
                        Chip("하루 ${daily}개", true, false, Modifier.weight(2f)) { }
                        Chip("+", false, !busy && daily < 5, Modifier.weight(1f)) { daily++ }
                    }
                    repeat(daily) { index ->
                        Action("${index + 1}번 슬롯 발행 ${slotTimes[index]}", busy) {
                            val initial = LocalTime.parse(slotTimes[index])
                            TimePickerDialog(context, { _, h, m ->
                                slotTimes = slotTimes.toMutableList().also { it[index] = LocalTime.of(h, m).toString() }
                            }, initial.hour, initial.minute, true).show()
                        }
                    }
                    Action("그룹 설정 저장", busy, groupId != null) { runAction("그룹 설정") {
                        dev.updateSettings(groupId!!, daily, slotTimes.take(daily))
                        store.applyServerGroup(groups.detail(groupId!!, session.account.userId))
                        "완료"
                    } }
                }
                Section("초기화") {
                    Action("로컬 캐시 초기화", busy) { runAction("로컬 캐시") {
                        store.reset(); refreshGroups(); "완료"
                    } }
                    Action("토큰 삭제 후 로그아웃", busy) { onLogout() }
                    Action("선택 그룹 삭제", busy, groupId != null) { confirmDelete = true }
                }
                Section("네트워크 오류 주입") {
                    @Suppress("UNUSED_VARIABLE") val revision = faultRevision
                    DevFault.entries.forEach { fault ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("${fault.name} (${DevFaults.count(fault)})", false, !busy,
                                Modifier.weight(2f)) { DevFaults.set(fault, 0); faultRevision++ }
                            Chip("+1", false, !busy, Modifier.weight(1f)) {
                                DevFaults.set(fault, DevFaults.count(fault) + 1); faultRevision++
                            }
                            Chip("+5", false, !busy, Modifier.weight(1f)) {
                                DevFaults.set(fault, DevFaults.count(fault) + 5); faultRevision++
                            }
                        }
                    }
                }
                Section("개발자 모드") {
                    Action("개발자 모드 끄기", busy) {
                        DevFaults.clear(); DevMode.changeEnabled(false)
                        toast.show("개발자 모드를 껐어요 · 서버 시계는 필요하면 초기화하세요")
                        onBack()
                    }
                }
                Section("최근 결과") {
                    SelectionContainer { Text(results.joinToString("\n").ifBlank { "아직 결과가 없어요" },
                        style = HType.caption13, color = HColors.ink) }
                }
            }
        }
        HToastHost(toast)
    }
    if (confirmReset) HAlert("[DEV] 그룹을 모두 삭제할까요?", "서버의 모든 [DEV] 그룹이 삭제돼요.",
        "전체 삭제", onCancel = { confirmReset = false }, onConfirm = {
            confirmReset = false
            runAction("전체 삭제") { val result = dev.reset(); DevMode.setOffset(0); refreshGroups();
                groupId = store.groups.firstOrNull()?.id; "${result.deletedGroups}개 삭제" }
        })
    if (confirmDelete) HAlert("선택 그룹을 삭제할까요?", "#${groupId ?: ""} 그룹과 기록이 삭제돼요.",
        "그룹 삭제", onCancel = { confirmDelete = false }, onConfirm = {
            confirmDelete = false
            runAction("그룹 삭제") { val id = groupId!!; dev.deleteGroup(id); store.leave(id)
                groupId = store.groups.firstOrNull()?.id; "#$id 삭제" }
        })
    rerollConfirmation?.let { (selectedGroupId, selectedMissionId, selectedTemplate) ->
        HAlert("이 미션을 새로 뽑을까요?", "이미 낸 제출·반응·댓글·콕이 모두 삭제돼요.", "리롤",
            onCancel = { rerollConfirmation = null }, onConfirm = {
                rerollConfirmation = null
                runAction("미션 리롤") {
                    val result = dev.reroll(selectedMissionId, selectedTemplate)
                    store.clearMissionForReroll(selectedGroupId, selectedMissionId)
                    submissionRows = emptyList()
                    selectedSubmission = null
                    refreshMission()
                    "${result.template} · ${result.content}"
                }
            })
    }
    if (showDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = clockDate.atStartOfDay(ZoneOffset.UTC)
            .toInstant().toEpochMilli())
        ModalBottomSheet(onDismissRequest = { showDate = false }, containerColor = HColors.bg) {
            Column(Modifier.padding(16.dp)) {
                DatePicker(state = picker)
                HButton("날짜 선택") {
                    picker.selectedDateMillis?.let { clockDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }
            }
        }
    }
}

private val templates = listOf("PHOTO_NOW", "PHOTO_GALLERY", "PHOTO_COLOR", "TEXT_ACROSTIC",
    "TEXT_ONE_WORD", "TEXT_REVIEW", "TEXT_BALANCE", "TEXT_FILL_BLANK", "DRAW_SKETCH",
    "DRAW_A_TO_B", "SLIDER_POSITION", "SLIDER_TASTE", "SLIDER_CONSENSUS")
private val stickers = listOf("heart", "laugh", "wow", "sad", "fire")

@Composable private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(HColors.surface, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = HType.plex(17, FontWeight.Bold), color = HColors.ink)
        content()
    }
}

@Composable private fun Action(title: String, busy: Boolean, available: Boolean = true,
    onClick: () -> Unit) {
    HButton(title, kind = HButtonKind.SECONDARY, height = 42.dp, enabled = !busy && available, onClick = onClick)
}

@Composable private fun Chip(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier = Modifier,
    onClick: () -> Unit) {
    Text(label, modifier.background(if (selected) HColors.accentSubtle else HColors.bg, RoundedCornerShape(10.dp))
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        style = HType.caption13, color = HColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun FlowChips(items: List<Pair<Long, String>>, selected: Long?, busy: Boolean,
    onClick: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (id, label) -> Chip(label, id == selected, !busy) { onClick(id) } }
    }
}
