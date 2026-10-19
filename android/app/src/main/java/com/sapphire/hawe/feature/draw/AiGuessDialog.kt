package com.sapphire.hawe.feature.draw

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.components.HButton
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HRadius
import com.sapphire.hawe.designsystem.tokens.HType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AiGuessState {
    data object Thinking : AiGuessState
    data class Done(val verdict: AiVerdict, val mine: Boolean) : AiGuessState
    data class Failed(val message: String) : AiGuessState
}

/** 제출 전 AI 미리보기 카드 상태를 들고 있어요. */
class AiGuessRunner(private val context: Context, private val scope: CoroutineScope) {
    var state by mutableStateOf<AiGuessState?>(null)
        private set
    private var job: Job? = null

    /** [load] 는 그림 픽셀을, [subjectOf] 는 맞힌 걸로 칠 범주들(없으면 빈 목록, [DrawSubjectMatcher.subjects])을 줘요. */
    fun guess(mine: Boolean, load: suspend () -> InkImage?, subjectOf: (List<DrawCategory>) -> List<DrawCategory>) {
        job?.cancel()
        state = AiGuessState.Thinking
        job = scope.launch {
            val started = System.currentTimeMillis()
            val outcome = try {
                val classifier = QuickDrawClassifier.get(context)
                val ink = classifier?.let { load() }
                when {
                    classifier == null -> AiGuessState.Failed("AI가 지금은 쉬고 있어요")
                    ink == null -> AiGuessState.Failed("그림을 불러오지 못했어요")
                    else -> {
                        val input = withContext(Dispatchers.Default) { ink.modelInput() }
                        val guesses = input?.let { topGuesses(classifier.classify(it), classifier.categories) }
                        AiGuessState.Done(aiVerdict(guesses, subjectOf(classifier.categories)), mine)
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                AiGuessState.Failed("AI가 지금은 쉬고 있어요")
            }
            delay((MIN_THINKING_MS - (System.currentTimeMillis() - started)).coerceAtLeast(0))  // 임시 연출
            state = outcome
        }
    }

    fun dismiss() { job?.cancel(); state = null }

    private companion object { const val MIN_THINKING_MS = 800L }
}

@Composable
fun rememberAiGuessRunner(): AiGuessRunner {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember(context, scope) { AiGuessRunner(context, scope) }
}

/** 모델을 쓸 수 있을 때만 true. 불러오지 못하면 버튼을 숨겨요. */
@Composable
fun rememberAiGuessAvailable(): Boolean {
    val context = LocalContext.current.applicationContext
    val available by produceState(false, context) { value = QuickDrawClassifier.get(context) != null }
    return available
}

/** 결과 카드(임시 UI: Figma 확정 시 교체). 1~3등 확률 막대와 판정 문구. */
@Composable
fun AiGuessDialog(runner: AiGuessRunner) {
    val state = runner.state ?: return
    Dialog(onDismissRequest = runner::dismiss) {
        Column(Modifier.fillMaxWidth().background(HColors.bg, RoundedCornerShape(HRadius.largeCard)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HIconView(HIcon.AI_GUESS_TEMP, size = 24.dp, color = HColors.accentPressed)
                Text("AI의 추측", style = HType.plex(14, FontWeight.SemiBold), color = HColors.accentPressed)
            }
            when (state) {
                AiGuessState.Thinking -> Row(Modifier.heightIn(min = 72.dp).semantics { contentDescription = "AI가 생각 중이에요" },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = HColors.accent, strokeWidth = 2.5.dp)
                    Text("AI가 생각 중…", style = HType.plex(18, FontWeight.Bold), color = HColors.ink)
                }
                is AiGuessState.Failed -> Text(state.message, style = HType.plex(18, FontWeight.Bold), color = HColors.ink)
                is AiGuessState.Done -> {
                    val copy = aiVerdictCopy(state.verdict, state.mine)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(copy.title, style = HType.plex(20, FontWeight.Bold, 1.35f), color = HColors.ink)
                        Text(copy.detail, style = HType.plex(14, lineHeightMultiple = 1.5f), color = HColors.textSecondary)
                    }
                    val guesses = state.verdict.guessesOrEmpty()
                    if (guesses.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        guesses.forEachIndexed { index, guess -> GuessBar(index + 1, guess, highlighted = index == 0) }
                    }
                }
            }
            if (state is AiGuessState.Done) Text("AI 추측은 재미용이에요. 그림은 휴대폰 안에서 분석하고, 올리면 별이의 추측을 그룹에 보여줘요.",
                style = HType.caption12, color = HColors.textTertiary)
            HButton("닫기", height = 48.dp, onClick = runner::dismiss)
        }
    }
}

@Composable
private fun GuessBar(rank: Int, guess: AiGuess, highlighted: Boolean) {
    val percent = percentText(guess.probability)
    Column(Modifier.clearAndSetSemantics { contentDescription = "${rank}등 ${guess.category.ko} $percent" },
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$rank  ${guess.category.ko}", Modifier.weight(1f), style = HType.plex(15,
                if (highlighted) FontWeight.Bold else FontWeight.Medium), color = HColors.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(percent, style = HType.caption13.copy(fontFeatureSettings = "tnum"), color = HColors.textSecondary)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).background(HColors.surfaceStrong, CircleShape)) {
            val fraction = guess.probability.coerceIn(0f, 1f)
            if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).fillMaxHeight()
                .background(if (highlighted) HColors.accent else HColors.textTertiary, CircleShape))
        }
    }
}

/** 그림 화면의 "AI에게 보여주기" 버튼(올리기 옆). */
@Composable
fun AiShowButton(enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(HRadius.button)
    Row(modifier.height(56.dp).background(HColors.bg, shape).border(1.dp, HColors.border, shape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        val color = if (enabled) HColors.ink else HColors.textDisabled
        HIconView(HIcon.AI_GUESS_TEMP, size = 20.dp, color = color)
        Text("AI에게 보여주기", style = HType.plex(15, FontWeight.SemiBold), color = color, maxLines = 1)
    }
}
