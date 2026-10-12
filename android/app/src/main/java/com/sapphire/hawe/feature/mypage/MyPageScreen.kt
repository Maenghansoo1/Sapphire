package com.sapphire.hawe.feature.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.AppLinks
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.COMING_SOON
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.model.ActivityStats
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.layout.ctaBottomPadding
import com.sapphire.hawe.designsystem.tokens.*

@Composable
fun rememberAppVersionName(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "1.0.0"
    }
}

@Composable
fun MyPageScreen(session: AppSession, store: DataStore, onBack: () -> Unit,
    onProfileEdit: () -> Unit, onNotifications: () -> Unit, onUserInfo: () -> Unit,
    onAppInfo: () -> Unit, onWithdraw: () -> Unit) {
    val context = LocalContext.current
    val account = session.account
    val stats = store.stats
    val version = rememberAppVersionName()
    var confirmLogout by remember { mutableStateOf(false) }
    val toast = rememberHToastState()
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(HColors.bg).padding(horizontal = HLayout.screenPaddingCompact)) {
        HNavBar("마이페이지", leading = HNavLeading.BACK, onLeading = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HAvatar(account.profile.avatar, size = 76.dp, characterScale = 60f / 76f)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(account.profile.nickname, style = HType.plex(22, FontWeight.Bold), color = HColors.ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val lines = accountLines(account)
                    if (lines.isNotEmpty()) Text(lines.joinToString("\n"),
                        style = HType.plex(13, lineHeightMultiple = 1.45f), color = HColors.textSecondary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.height(44.dp).clickable { toast.show(COMING_SOON) }, contentAlignment = Alignment.Center) {
                    Box(Modifier.height(36.dp).border(1.dp, HColors.border, CircleShape)
                        .padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text("프로필 편집", style = HType.plex(13, FontWeight.SemiBold), color = HColors.ink,
                            maxLines = 1)
                    }
                }
            }
            StatsCard(stats, Modifier.padding(top = 22.dp))
            TypeCounts(stats, Modifier.padding(top = 14.dp))
            Column(Modifier.padding(top = 18.dp)) {
                HListRow("알림 설정", height = 48.dp, onClick = onNotifications)
                HListRow("유저 정보", height = 48.dp, onClick = onUserInfo)
                HListRow("앱 정보", height = 48.dp, onClick = onAppInfo)
                HListRow("이용약관", height = 48.dp, onClick = {
                    runCatching { openInCustomTab(context, AppLinks.termsOfService) }
                })
                HListRow("개인정보 처리방침", height = 48.dp,
                    onClick = { runCatching { openInCustomTab(context, AppLinks.privacyPolicy) } })
                DangerRow("로그아웃", true) { confirmLogout = true }
                DangerRow("회원 탈퇴", false) { toast.show(COMING_SOON) }
            }
        }
        Text("버전 $version", Modifier.fillMaxWidth().padding(top = 12.dp, bottom = ctaBottomPadding(24.dp)),
            style = HType.caption12, color = HColors.textSecondary, textAlign = TextAlign.Center)
    }
    if (confirmLogout) HAlert("로그아웃할까요?", "다시 로그인하면 그룹과 기록은\n그대로 남아 있어요.",
        "로그아웃", onCancel = { confirmLogout = false }, onConfirm = { session.signOut() })
    HToastHost(toast)
    }
}

@Composable
private fun StatsCard(stats: ActivityStats, modifier: Modifier = Modifier) {
    val display = statsDisplay(stats)
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)
        .background(HColors.surface, RoundedCornerShape(18.dp)).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        StatCell(display.completed, "완료한 미션", Modifier.weight(1f))
        Spacer(Modifier.width(1.dp).fillMaxHeight().background(HColors.disabled))
        StatCell(display.streak, "연속 참여", Modifier.weight(1f))
        Spacer(Modifier.width(1.dp).fillMaxHeight().background(HColors.disabled))
        StatCell(display.groups, "참여 그룹", Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = HType.plex(20, FontWeight.Bold), color = HColors.ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = HType.caption12, color = HColors.textSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TypeCounts(stats: ActivityStats, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MissionType.entries.forEach { type ->
            Column(Modifier.weight(1f).border(1.dp, HColors.line, RoundedCornerShape(HRadius.input))
                .padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(type.displayName, style = HType.caption12, color = HColors.textSecondary,
                    maxLines = 1)
                Text(byTypeCount(stats, type).toString(), style = HType.plex(15, FontWeight.Bold),
                    color = HColors.ink, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DangerRow(label: String, divider: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick)) {
        Text(label, Modifier.align(Alignment.CenterStart), style = HType.plex(15), color = HColors.danger)
        if (divider) Spacer(Modifier.fillMaxWidth().height(1.dp).align(Alignment.BottomCenter)
            .background(HColors.line))
    }
}
