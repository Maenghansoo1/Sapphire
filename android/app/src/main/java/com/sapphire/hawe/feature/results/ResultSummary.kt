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
import com.sapphire.hawe.core.model.AvatarContent
import com.sapphire.hawe.core.model.GroupMember
import com.sapphire.hawe.core.model.MissionAggregate
import com.sapphire.hawe.core.model.MissionSpec
import com.sapphire.hawe.designsystem.assets.ProfileCharacter
import com.sapphire.hawe.designsystem.components.HAvatar
import com.sapphire.hawe.designsystem.components.HSliderEndLabels
import com.sapphire.hawe.designsystem.components.HSliderMark
import com.sapphire.hawe.designsystem.components.HSliderTrack
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

/**
 * 집계 틀의 "그룹 결과" 카드(임시 UI: Figma 확정 시 교체). 값은 모두 서버가 계산한 그대로 보여요.
 * 밸런스는 A/B 비율 막대, 슬라이더는 평균·분포 점·내 위치 차이, 다수결 예측은 실제 평균과 순위표.
 */
@Composable
fun AggregateCard(load: AggregateLoad, spec: MissionSpec, members: List<GroupMember>, myUserId: Long,
    onRetry: () -> Unit, modifier: Modifier = Modifier) {
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
                is MissionAggregate.Slider -> SliderSummary(value, spec, myUserId)
                is MissionAggregate.Consensus -> ConsensusSummary(value, spec, members, myUserId)
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
private fun SliderSummary(value: MissionAggregate.Slider, spec: MissionSpec, myUserId: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AverageLine("평균", value.average)
        HSliderTrack(value.distribution.map {
            HSliderMark(it.number, if (it.userId == myUserId) HColors.accent else HColors.textTertiary)
        }, Modifier.clearAndSetSemantics {
            contentDescription = value.distribution.joinToString(", ") { "${it.nickname} ${it.number}" }
        }, average = value.average)
        HSliderEndLabels(spec.left, spec.right)
        diffFromAverageText(value.diffFromAverage, spec)?.let {
            Text(it, style = HType.plex(14, FontWeight.SemiBold), color = HColors.ink)
        }
    }
}

@Composable
private fun ConsensusSummary(value: MissionAggregate.Consensus, spec: MissionSpec, members: List<GroupMember>,
    myUserId: Long) {
    val ranks = competitionRanks(value.ranking)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AverageLine("실제 평균", value.average)
        HSliderTrack(value.ranking.map {
            HSliderMark(it.guess, if (it.userId in value.winnerUserIds) HColors.accent else HColors.textTertiary)
        }, Modifier.clearAndSetSemantics { }, average = value.average)
        HSliderEndLabels(spec.left, spec.right)
        consensusWinnerText(value.ranking, value.winnerUserIds, myUserId)?.let {
            Text(it, style = HType.plex(14, FontWeight.SemiBold), color = HColors.ink)
        }
        Column {
            value.ranking.forEachIndexed { index, entry ->
                val avatar = members.firstOrNull { it.id == entry.userId }?.displayed?.avatar
                val name = if (entry.userId == myUserId) "${entry.nickname} (나)" else entry.nickname
                val winner = entry.userId in value.winnerUserIds
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clearAndSetSemantics {
                    contentDescription = "${ranks[index]}등 $name, 예측 ${entry.guess}, 차이 ${decimalText(entry.diff)}" +
                        if (winner) ", 가장 가깝게 맞힘" else ""
                }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${ranks[index]}", Modifier.width(18.dp), style = HType.plex(15, FontWeight.Bold)
                        .copy(fontFeatureSettings = "tnum"), color = if (winner) HColors.accentPressed else HColors.ink)
                    HAvatar(avatar ?: AvatarContent.Character(ProfileCharacter.DEFAULT), size = 28.dp)
                    Text(name, Modifier.weight(1f), style = HType.plex(14, FontWeight.SemiBold), color = HColors.ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (winner) MineTag("적중")
                    Text("예측 ${entry.guess} · 차이 ${decimalText(entry.diff)}",
                        style = HType.caption12.copy(fontFeatureSettings = "tnum"), color = HColors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun AverageLine(label: String, average: Double?) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.padding(bottom = 4.dp), style = HType.caption13, color = HColors.textSecondary)
        Text(average?.let(::decimalText) ?: "-", style = HType.plex(28, FontWeight.Bold)
            .copy(fontFeatureSettings = "tnum"), color = HColors.ink)
    }
}

@Composable
private fun MineTag(text: String) {
    Box(Modifier.background(HColors.accentSubtle, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, style = HType.plex(12, FontWeight.SemiBold), color = HColors.accentPressed)
    }
}
