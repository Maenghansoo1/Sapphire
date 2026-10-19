package com.sapphire.hawe.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.dev.AppClock
import com.sapphire.hawe.core.model.AppNotification
import com.sapphire.hawe.core.net.NotificationRepository
import com.sapphire.hawe.core.net.toUserMessage
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.layout.ctaBottomPadding
import com.sapphire.hawe.designsystem.tokens.*
import com.sapphire.hawe.feature.results.koreanRelativeText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface NotificationLoad {
    data object Loading : NotificationLoad
    data object Failed : NotificationLoad
    data class Loaded(val items: List<AppNotification>) : NotificationLoad
}

/** 알림 목록 — 콕·댓글·멘션·반응·미션 도착. 누르면 읽음으로 바꾸고 그 화면으로 가요. */
@Composable
fun NotificationsScreen(repository: NotificationRepository, store: DataStore, onBack: () -> Unit,
    onOpen: (NotificationTarget) -> Unit) {
    var load by remember { mutableStateOf<NotificationLoad>(NotificationLoad.Loading) }
    var retry by remember { mutableIntStateOf(0) }
    val toast = rememberHToastState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(retry) {
        if (load !is NotificationLoad.Loaded) load = NotificationLoad.Loading
        load = try {
            NotificationLoad.Loaded(repository.list())
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            NotificationLoad.Failed
        }
    }
    fun update(transform: (List<AppNotification>) -> List<AppNotification>) {
        (load as? NotificationLoad.Loaded)?.let { load = it.copy(items = transform(it.items)) }
    }
    fun open(notification: AppNotification) {
        if (!notification.isRead) {
            update { items -> items.map { if (it.id == notification.id) it.copy(isRead = true) else it } }
            scope.launch {
                try { repository.markRead(notification.id) } catch (error: Exception) { if (error is CancellationException) throw error }
            }
        }
        val missionId = notification.missionId
        val canOpenResults = missionId != null && (store.todayMission(notification.groupId, missionId)?.didSubmit
            ?: store.calendarRecord(notification.groupId, missionId)?.didSubmit ?: false)
        when (val target = notificationTarget(notification, store.group(notification.groupId) != null, canOpenResults)) {
            NotificationTarget.Missing -> toast.show("더 이상 볼 수 없는 그룹이에요")
            else -> onOpen(target)
        }
    }
    val loaded = load as? NotificationLoad.Loaded
    val hasUnread = loaded?.items?.any { !it.isRead } == true
    Box(Modifier.fillMaxSize().background(HColors.bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = HLayout.screenPaddingCompact)) {
            HNavBar("알림", leading = HNavLeading.BACK, onLeading = onBack, trailing = {
                if (hasUnread) Box(Modifier.heightIn(min = HLayout.minTouch).clickable {
                    val before = loaded?.items.orEmpty()
                    update { items -> items.map { it.copy(isRead = true) } }
                    scope.launch {
                        try { repository.markAllRead() } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            update { before }
                            toast.show(error.toUserMessage())
                        }
                    }
                }, contentAlignment = Alignment.Center) {
                    Text("모두 읽음", style = HType.plex(14, FontWeight.SemiBold), color = HColors.ink)
                }
            })
            when (val state = load) {
                NotificationLoad.Loading -> Spacer(Modifier.weight(1f))
                NotificationLoad.Failed -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("알림을 불러오지 못했어요", style = HType.sectionTitle, color = HColors.ink)
                    Spacer(Modifier.height(16.dp))
                    HButton("다시 시도") { retry++ }
                }
                is NotificationLoad.Loaded -> if (state.items.isEmpty()) EmptyNotifications()
                    else NotificationList(state.items, ::open)
            }
        }
        HToastHost(toast)
    }
}

@Composable
private fun EmptyNotifications() {
    Column(Modifier.fillMaxSize().padding(bottom = 80.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).background(HColors.surface, CircleShape), contentAlignment = Alignment.Center) {
            HIconView(HIcon.BELL, size = 28.dp, color = HColors.textTertiary)
        }
        Spacer(Modifier.height(16.dp))
        Text("아직 받은 알림이 없어요", style = HType.sectionTitle, color = HColors.ink)
        Spacer(Modifier.height(6.dp))
        Text("콕, 댓글, 반응과 새 미션 소식이 여기에 모여요", style = HType.caption13,
            color = HColors.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NotificationList(items: List<AppNotification>, onOpen: (AppNotification) -> Unit) {
    val now = AppClock.now()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = ctaBottomPadding(24.dp))) {
        notificationSections(items, now).forEach { (section, rows) ->
            Text(section.title, Modifier.padding(top = 16.dp, bottom = 4.dp),
                style = HType.plex(13, FontWeight.SemiBold), color = HColors.textSecondary)
            rows.forEach { NotificationRow(it, now) { onOpen(it) } }
        }
    }
}

@Composable
private fun NotificationRow(notification: AppNotification, now: java.time.Instant, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp)) {
                val from = notification.from
                if (from != null) HAvatar(from.avatar, size = 40.dp)
                else Box(Modifier.size(40.dp).background(HColors.accentSubtle, CircleShape), contentAlignment = Alignment.Center) {
                    HIconView(HIcon.BELL, size = 20.dp, color = HColors.accentPressed)
                }
                if (from != null) Box(Modifier.size(20.dp).align(Alignment.BottomEnd).background(HColors.bg, CircleShape)
                    .border(1.dp, HColors.line, CircleShape), contentAlignment = Alignment.Center) {
                    HIconView(notificationBadgeIcon(notification.type), size = 12.dp, color = HColors.accentPressed)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(notificationText(notification), style = HType.plex(15,
                    if (notification.isRead) FontWeight.Normal else FontWeight.SemiBold, 1.45f),
                    color = if (notification.isRead) HColors.textBody else HColors.ink,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${notification.groupName} · ${koreanRelativeText(notification.createdAt, now)}",
                    style = HType.caption12, color = HColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!notification.isRead) Box(Modifier.padding(top = 7.dp).size(8.dp).background(HColors.accent, CircleShape))
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(HColors.line))
    }
}
