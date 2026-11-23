package com.sapphire.hawe.feature.draw

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.sapphire.hawe.core.josa
import com.sapphire.hawe.core.model.MissionSpec
import com.sapphire.hawe.feature.mission.DrawStroke
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 그리는 중 추론할 그림 한 장(획 + 그림판 크기 dp). 메인 스레드에서 뜬 복사본이에요. */
class LiveInk(val strokes: List<DrawStroke>, val widthDp: Float, val heightDp: Float)

/**
 * 그리는 중 계속 별이(AI)가 추측하게 해요(TODO_2/A2). 획이 끝나면 바로, 그리는 도중에는 0.5초에 한 번만 추론하고([InferenceGate]),
 * 결과를 [LiveGuessTracker] 로 안정화해서 [state] 에 둬요. 메인 스레드에서만 불러 주세요.
 * 그림·추론 결과는 메모리에만 있고 저장·전송하지 않아요. 올릴 때는 지금처럼 [guessForUpload] 를 따로 불러요.
 */
class LiveGuessRunner internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val subjectsOf: (List<DrawCategory>) -> List<DrawCategory>,
    private val now: () -> Long,
) {
    var state by mutableStateOf(LiveGuessState())
        private set
    /** 모델을 불러오지 못하면 false — 말풍선을 숨겨요. */
    var available by mutableStateOf(true)
        private set

    private val gate = InferenceGate()
    private var tracker: LiveGuessTracker? = null
    private var startedAtMillis: Long? = null  // 모델을 불러오기 전에 그리기 시작한 시각
    private var latest: (() -> LiveInk?)? = null

    /** 펜 획을 긋기 시작할 때. 그림의 첫 획 시각부터 "○초 만에"를 세요. */
    fun onStrokeStart() {
        val at = now()
        val active = tracker
        if (active != null) active.onStrokeStart(at) else if (startedAtMillis == null) startedAtMillis = at
    }

    /**
     * 그림이 바뀌었을 때(그리는 중, 획 끝, 되돌리기). [ink] 는 추론을 시작할 때 불러서 그 순간의 그림을 떠요.
     * [hasInk] 가 true 인데 아직 추측이 없으면 "지켜보는 중"을 보여 줘요.
     */
    fun request(strokeEnded: Boolean, hasInk: Boolean, ink: () -> LiveInk?) {
        if (!available) return
        latest = ink
        if (hasInk && state.guesses.isEmpty() && state.status != LiveGuessStatus.Thinking)
            state = tracker?.onInkPending() ?: state.copy(status = LiveGuessStatus.Thinking)
        if (gate.request(now(), strokeEnded)) launchInference()
    }

    private fun launchInference() {
        scope.launch {
            try { infer() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { /* 이번 결과만 건너뛰어요. 다음 획에서 다시 해요 */ }
            finally { if (gate.finish(now())) launchInference() }
        }
    }

    private suspend fun infer() {
        val classifier = QuickDrawClassifier.get(context)
        if (classifier == null) { available = false; return }
        val active = tracker ?: subjectsOf(classifier.categories).let { subjects ->
            LiveGuessTracker(subjects = subjects, now = now).also { created ->
                startedAtMillis?.let(created::onStrokeStart)
                startedAtMillis = null
                tracker = created
            }
        }
        val ink = latest?.invoke() ?: return
        val input = withContext(Dispatchers.Default) { strokeInkImage(ink.strokes, ink.widthDp, ink.heightDp).modelInput() }
        state = if (input == null) active.onEmpty()
            else active.onInference(classifier.classify(input), classifier.categories, now())
    }
}

/** 모델을 쓸 수 있을 때만 true. 불러오지 못하면 말풍선을 숨겨요. */
@Composable
fun rememberAiGuessAvailable(): Boolean {
    val context = LocalContext.current.applicationContext
    val available by produceState(false, context) { value = QuickDrawClassifier.get(context) != null }
    return available
}

/** 그림 미션 화면용. 주제(틀·문장·명세)가 같으면 같은 인스턴스를 돌려줘요. 화면을 떠나면 진행 중 추론도 취소돼요. */
@Composable
fun rememberLiveGuess(template: String, topic: String, spec: MissionSpec): LiveGuessRunner {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember(context, scope, template, topic, spec) {
        LiveGuessRunner(context, scope, { DrawSubjectMatcher.subjects(template, topic, spec, it) }, SystemClock::elapsedRealtime)
    }
}

/**
 * 말풍선 문구. 앱 안에서 AI 는 '별이'예요(이모지는 쓰지 않아요).
 * [candidates] 는 말풍선 아래 칩으로 보여 줄 상위 후보(최대 2개), [badge] 는 이미 맞힌 뒤 그림이 바뀌었을 때 남기는 표시예요.
 * 그린 사람을 탓하지 않는 말투로(못 알아봐도 별이가 헷갈리는 걸로).
 */
data class LiveGuessCopy(val title: String, val detail: String?, val candidates: List<AiGuess>, val badge: String?,
    val celebrate: Boolean) {
    /** 화면 낭독용 설명 */
    val description: String get() = listOfNotNull(title, detail,
        candidates.joinToString(", ") { "${it.category.ko} ${percentText(it.probability)}" }.ifEmpty { null }, badge)
        .joinToString(", ")
}

fun liveGuessCopy(state: LiveGuessState): LiveGuessCopy {
    val top = state.guesses.firstOrNull()?.category?.ko
    val candidates = state.guesses.take(2)
    val badge = if (state.guessed && state.status != LiveGuessStatus.Correct)
        state.elapsedMillis?.let { "${elapsedSeconds(it)}초 만에 맞혔어요" } ?: "맞혔어요" else null
    return when (state.status) {
        LiveGuessStatus.TooEmpty -> LiveGuessCopy("그리면 별이가 맞혀 볼게요", "그리는 동안 계속 추측해요", emptyList(), badge, false)
        LiveGuessStatus.Thinking -> LiveGuessCopy("별이가 지켜보는 중…", null, emptyList(), badge, false)
        LiveGuessStatus.Unsure -> LiveGuessCopy("음… 아직 모르겠어요", null, candidates, badge, false)
        LiveGuessStatus.Guessed -> LiveGuessCopy(
            if (top == null) "음… 아직 모르겠어요" else "별이는 ‘$top’${josa(top, "이라고", "라고")} 생각해요",
            null, candidates, badge, false)
        LiveGuessStatus.Correct -> LiveGuessCopy("별이가 맞혔어요!",
            listOfNotNull(top?.let { "‘$it’" }, state.elapsedMillis?.let { "${elapsedSeconds(it)}초 만에" })
                .joinToString(" · ").ifEmpty { null }, emptyList(), null, true)
    }
}

/** 반올림한 초, 최소 1초(0초 만에 맞혔다고 하지 않게) */
internal fun elapsedSeconds(millis: Long): Long = ((millis.coerceAtLeast(0) + 500) / 1000).coerceAtLeast(1)
