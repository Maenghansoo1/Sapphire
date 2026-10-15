package com.sapphire.hawe.feature.groups

import androidx.compose.foundation.background
import com.sapphire.hawe.core.dev.AppClock
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.data.DataStore
import com.sapphire.hawe.core.model.GroupSummary
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.components.MemberRow
import com.sapphire.hawe.designsystem.layout.ctaBottomPadding
import com.sapphire.hawe.designsystem.tokens.*
import java.time.Instant

@Composable
fun HomeScreen(store: DataStore, onAdd: () -> Unit, onGroup: (Long) -> Unit,
    onMyPage: () -> Unit) {
    val groups = store.summaries
    val bottom = ctaBottomPadding()
    Box(Modifier.fillMaxSize().background(HColors.bg)) {
        Column {
            Row(Modifier.fillMaxWidth().height(HLayout.headerHeight).padding(horizontal = HLayout.screenPaddingCompact),
                verticalAlignment = Alignment.CenterVertically) {
                Text("내 그룹", style = HType.screenTitle, color = HColors.ink)
                Spacer(Modifier.weight(1f))
                HeaderIcon(HIcon.PLUS, 24, "그룹 추가", onAdd)
                Spacer(Modifier.width(16.dp))
                HeaderIcon(HIcon.PERSON, 22, "마이페이지", onMyPage)
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = HLayout.screenPaddingCompact)
                .padding(bottom = bottom + 24.dp)) {
                Text("${homeDateText(AppClock.now())} · 그룹 ${groups.size}개",
                    Modifier.padding(top = 6.dp, bottom = 20.dp), style = HType.plex(14), color = HColors.textSecondary)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    groups.forEach { group ->
                        GroupCard(group, Modifier.fillMaxWidth().clickable { onGroup(group.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderIcon(icon: HIcon, size: Int, label: String, onClick: () -> Unit) {
    Box(Modifier.size(HLayout.minTouch).clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center) { HIconView(icon, size = size.dp) }
}

@Composable
fun GroupCard(group: GroupSummary, modifier: Modifier = Modifier) {
    Column(modifier.border(1.dp, HColors.line, RoundedCornerShape(HRadius.card)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(group.name, style = HType.plex(18, FontWeight.Bold), color = HColors.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(groupCardSubtitle(group), style = HType.caption13, color = HColors.textSecondary)
        }
        MemberRow(group.members, group.relationship)
        Column(Modifier.fillMaxWidth().background(HColors.surface, RoundedCornerShape(HRadius.input)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (group.missionType == null) {
                Text("아직 오늘의 미션이 열리지 않았어요", style = HType.plex(15, FontWeight.SemiBold),
                    color = HColors.ink)
            } else {
                Text(homeMissionLabel(group),
                    style = HType.plex(12, FontWeight.SemiBold), color = HColors.textSecondary)
                Text(group.topic.orEmpty(), style = HType.plex(15, FontWeight.SemiBold), color = HColors.ink)
            }
        }
        if (group.missionType != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${group.submittedCount} / ${group.members.size}명 완료", style = HType.caption13,
                color = HColors.textSecondary)
            homeSubmissionProgress(group)?.let {
                Text(" · $it", style = HType.caption13, color = HColors.textSecondary)
            }
            Spacer(Modifier.weight(1f))
            Text(if (group.didSubmit) "열어보기" else "내 미션 남음",
                Modifier.height(28.dp).background(if (group.didSubmit) HColors.surface else HColors.accent, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                style = HType.plex(13, FontWeight.SemiBold),
                color = if (group.didSubmit) HColors.ink else HColors.onAccent)
        }
    }
}
