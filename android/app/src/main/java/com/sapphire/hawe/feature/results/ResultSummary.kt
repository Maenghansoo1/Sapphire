package com.sapphire.hawe.feature.results

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.model.MissionAggregate
import com.sapphire.hawe.core.model.MissionSpec
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HRadius
import com.sapphire.hawe.designsystem.tokens.HType

/** 결과 카드 상태. 잠금·권한 오류는 피드 쪽이 화면을 닫으므로 여기서는 숨기기만 해요. */
sealed interface AggregateLoad {
    data object Loading : AggregateLoad
    data object Failed : AggregateLoad
    data object Hidden : AggregateLoad
    data class Loaded(val value: MissionAggregate) : AggregateLoad
}

/** 집계 틀(밸런스 게임)의 "그룹 결과" 카드(임시 UI: Figma 확정 시 교체). 값은 서버가 계산한 A/B 비율 그대로예요. */
@Composable
fun AggregateCard(load: AggregateLoad, spec: MissionSpec, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    if (load == AggregateLoad.Hidden) return
    Column(modifier.fillMaxWidth().border(1.dp, HColors.line, RoundedCornerShape(HRadius.largeCard))
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("그룹 결과", Modifier.weight(1f), style = HType.sectionTitle, color = HColors.ink)
            if (load is AggregateLoad.Loaded) Text("${load.value.totalCount}명 참여", style = HType.caption13,
                color = HColors.textSecondary)
        }
        when (load) {
            AggregateLoad.Loading -> Text("결과를 불러오는 중이에요", style = HType.caption13, color = HColors.textSecondary)
            AggregateLoad.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("결과를 불러오지 못했어요", Modifier.weight(1f), style = HType.plex(14), color = HColors.textBody)
                Box(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onRetry)
                    .padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text("다시 시도", style = HType.plex(14, FontWeight.SemiBold), color = HColors.accentPressed)
                }
            }
            AggregateLoad.Hidden -> Unit
            is AggregateLoad.Loaded -> when (val value = load.value) {
                is MissionAggregate.Balance -> BalanceSummary(value, spec)
            }
        }
    }
}

@Composable
private fun BalanceSummary(value: MissionAggregate.Balance, spec: MissionSpec) {
    val (percentA, percentB) = balancePercents(value.countA, value.countB)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BalanceBar("A", spec.optionA, value.countA, percentA, value.myChoice == "A")
        BalanceBar("B", spec.optionB, value.countB, percentB, value.myChoice == "B")
    }
}

@Composable
private fun BalanceBar(letter: String, option: String?, count: Int, percent: Int, mine: Boolean) {
    val name = option?.takeIf { it.isNotBlank() } ?: letter
    Column(Modifier.clearAndSetSemantics {
        contentDescription = "$letter $name, ${count}명, $percent 퍼센트${if (mine) ", 내 선택" else ""}"
    }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$letter · $name", Modifier.weight(1f), style = HType.plex(15, FontWeight.SemiBold),
                color = HColors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (mine) MineTag("내 선택")
            Text("${count}명 · $percent%", style = HType.caption13.copy(fontFeatureSettings = "tnum"),
                color = HColors.textSecondary)
        }
        Box(Modifier.fillMaxWidth().height(10.dp).background(HColors.surfaceStrong, CircleShape)) {
            if (percent > 0) Box(Modifier.fillMaxWidth(percent / 100f).fillMaxHeight()
                .background(if (mine) HColors.accent else HColors.textTertiary, CircleShape))
        }
    }
}

@Composable
private fun MineTag(text: String) {
    Box(Modifier.background(HColors.accentSubtle, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, style = HType.plex(12, FontWeight.SemiBold), color = HColors.accentPressed)
    }
}
