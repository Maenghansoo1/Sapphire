package com.sapphire.hawe.feature.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.model.NotificationPrefs
import com.sapphire.hawe.designsystem.components.HNavBar
import com.sapphire.hawe.designsystem.components.HNavLeading
import com.sapphire.hawe.designsystem.components.HToggle
import com.sapphire.hawe.designsystem.tokens.*

@Composable
fun NotificationSettingsScreen(session: AppSession, onBack: () -> Unit) {
    val prefs = session.notificationPrefs
    val update: (NotificationKey, Boolean) -> Unit = { key, value ->
        // TODO: 서버 연결 시 알림 설정을 저장해요.
        session.notificationPrefs = session.notificationPrefs.with(key, value)
    }
    Column(Modifier.fillMaxSize().background(HColors.bg).padding(horizontal = HLayout.screenPaddingCompact)) {
        HNavBar("알림 설정", leading = HNavLeading.BACK, onLeading = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)
                .background(HColors.surface, RoundedCornerShape(HRadius.button))
                .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("전체 알림", style = HType.plex(16, FontWeight.Bold), color = HColors.ink)
                    Text("끄면 모든 푸시 알림이 오지 않아요", style = HType.caption12,
                        color = HColors.textSecondary)
                }
                HToggle(prefs.all, { update(NotificationKey.ALL, it) }, contentDescription = "전체 알림")
            }
            SectionLabel("미션")
            NotificationRow("오늘의 주제 도착", "그룹마다 정한 시간에 알려드려요",
                prefs, NotificationKey.TOPIC, prefs.topic, update)
            SectionLabel("친구 활동")
            NotificationRow("콕 찌르기", null, prefs, NotificationKey.POKE, prefs.poke, update)
            NotificationRow("내 작품에 댓글", null, prefs, NotificationKey.COMMENT, prefs.comment, update)
            Text("그룹별 알림은 각 그룹의 '그룹 정보'에서 따로 끌 수 있어요.",
                Modifier.padding(top = 16.dp), style = HType.caption12, color = HColors.textSecondary)
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(label, Modifier.padding(top = 22.dp, bottom = 4.dp),
        style = HType.plex(13, FontWeight.SemiBold), color = HColors.textSecondary)
}

@Composable
private fun NotificationRow(label: String, subtitle: String?, prefs: NotificationPrefs,
    key: NotificationKey, value: Boolean, update: (NotificationKey, Boolean) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(enabled = prefs.all) { update(key, !value) }
            .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = HType.plex(15),
                    color = if (individualEnabled(prefs)) HColors.ink else HColors.textTertiary)
                if (subtitle != null) Text(subtitle, style = HType.caption12,
                    color = HColors.textSecondary)
            }
            HToggle(value, { update(key, it) }, enabled = individualEnabled(prefs),
                contentDescription = label)
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(HColors.line))
    }
}
