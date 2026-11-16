package com.sapphire.hawe.feature.draw

import kotlin.math.roundToInt

/**
 * 그리는 중 "AI가 맞혔어요"로 치는 주제 확률. 보정 전 임시값이라 TODO_2/A3 에서 직접 그려 보며 다시 정해요.
 * 제출 때 쓰는 [AI_GUESS_THRESHOLD](0.55)보다 조금 높게 잡아, 그리는 도중 잠깐 스친 확률로 맞혔다고 하지 않게 했어요.
 */
const val LIVE_CORRECT_THRESHOLD = 0.6f

/**
 * 실시간 추측 설정. 기본값은 TODO_2/A1 계획의 값이에요.
 * @param alpha 새 추론 결과를 얼마나 믿을지(지수이동평균). 1이면 평활 없이 마지막 결과 그대로, 0.5면 대략 최근 3회 평균.
 * @param margin 화면의 1등을 바꾸려면 새 후보 확률이 지금 1등보다 이만큼 높아야 해요(1등이 두 후보 사이에서 흔들리지 않게).
 * @param unsureThreshold 1등 확률이 이보다 낮으면 "잘 모르겠어요".
 * @param correctThreshold 주제 확률이 이 이상인 상태가 [requiredStreak]번 이어지면 맞힘.
 */
data class LiveGuessConfig(
    val alpha: Float = 0.5f,
    val margin: Float = 0.1f,
    val unsureThreshold: Float = AI_GUESS_THRESHOLD,
    val correctThreshold: Float = LIVE_CORRECT_THRESHOLD,
    val requiredStreak: Int = 2,
    val topCount: Int = 3,
) {
    init {
        require(alpha > 0f && alpha <= 1f) { "alpha 는 0보다 크고 1 이하여야 해요" }
        require(margin >= 0f) { "margin 은 0 이상이어야 해요" }
        require(requiredStreak >= 1) { "requiredStreak 는 1 이상이어야 해요" }
        require(topCount >= 1) { "topCount 는 1 이상이어야 해요" }
    }
}

/** 그리는 중 말풍선에 보여 줄 상태. 문구는 화면(A2)에서 정해요. */
enum class LiveGuessStatus {
    /** 그림이 비었거나 너무 작아서 추론하지 않음 */
    TooEmpty,
    /** 그림은 있지만 아직 첫 추론 결과가 없음(모델을 처음 불러오는 중 등) */
    Thinking,
    /** 1등 확률이 [LiveGuessConfig.unsureThreshold] 미만 */
    Unsure,
    /** 1등을 골랐지만 주제는 아님, 또는 주제를 범주로 알 수 없어 추측만 */
    Guessed,
    /** 주제를 맞힌 상태가 이어지는 중 */
    Correct,
}

/**
 * @param guesses 평활한 확률 기준 후보(첫 번째가 화면의 1등). 확률은 소수 셋째 자리까지 반올림해서, 같은 그림을 다시 추론하면 같은 상태가 나와요.
 * @param guessed 이번 그림에서 한 번이라도 맞혔는지. 지우개로 지워도 남고 [LiveGuessTracker.reset] 에서만 초기화.
 * @param guessedAtMillis 처음 맞힌 시각. 더 그려도 바뀌지 않아요.
 * @param elapsedMillis 그림 시작([LiveGuessTracker.onStrokeStart] 첫 호출)부터 처음 맞힐 때까지 걸린 시간. 시작 시각을 모르면 null.
 */
data class LiveGuessState(
    val status: LiveGuessStatus = LiveGuessStatus.TooEmpty,
    val guesses: List<AiGuess> = emptyList(),
    val guessed: Boolean = false,
    val guessedAtMillis: Long? = null,
    val elapsedMillis: Long? = null,
)

/**
 * 그리는 중 계속 들어오는 추론 결과를 화면에 보여 줄 "안정된 추측"과 "맞혔다" 판정으로 바꿔요.
 * UI·TFLite·코루틴과 무관한 순수 로직이라 JVM 테스트로 확인해요. 한 스레드(보통 메인)에서만 불러 주세요.
 * 결과는 메모리에만 있고 어디에도 저장·전송하지 않아요.
 *
 * @param subjects 맞힌 걸로 칠 범주([DrawSubjectMatcher.subjects]). 비어 있으면(주제를 범주로 모름, `[대상]` 미션) 판정 없이 추측만 해요.
 */
class LiveGuessTracker(
    private val config: LiveGuessConfig = LiveGuessConfig(),
    subjects: List<DrawCategory>,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val subjectIds = subjects.map { it.id }.toSet()
    private var smoothed: FloatArray? = null
    private var leader: Int? = null  // 화면의 1등(범주 목록의 위치)
    private var streak = 0
    private var startedAtMillis: Long? = null

    var state: LiveGuessState = LiveGuessState()
        private set

    /** 획을 긋기 시작할 때. 그림의 첫 획 시각만 기억해서 "○초 만에"를 계산해요. */
    fun onStrokeStart(atMillis: Long = now()) {
        if (startedAtMillis == null) startedAtMillis = atMillis
    }

    /** 그림은 있는데 추론 결과를 기다리는 중일 때. 이미 보여 주는 추측이 있으면 그대로 둬서 말풍선이 깜빡이지 않아요. */
    fun onInkPending(): LiveGuessState {
        if (state.guesses.isEmpty() && state.status != LiveGuessStatus.Thinking)
            state = state.copy(status = LiveGuessStatus.Thinking)
        return state
    }

    /**
     * 추론 결과 하나를 반영해요. [probabilities] 는 [categories] 순서의 확률이에요(모델 출력 그대로).
     * 길이가 다르거나 NaN·무한대가 섞이면 이번 결과는 버리고 지금 상태를 그대로 돌려줘요(크래시 없음).
     */
    fun onInference(probabilities: FloatArray, categories: List<DrawCategory>, atMillis: Long = now()): LiveGuessState {
        if (categories.isEmpty() || probabilities.size != categories.size || probabilities.any { !it.isFinite() }) return state
        val raw = FloatArray(probabilities.size) { probabilities[it].coerceIn(0f, 1f) }
        val previous = smoothed?.takeIf { it.size == raw.size }
        val values = if (previous == null) raw else FloatArray(raw.size) { config.alpha * raw[it] + (1 - config.alpha) * previous[it] }
        smoothed = values
        if (previous == null) leader = null

        val best = values.indices.maxWith(compareBy<Int> { values[it] }.thenByDescending { it })
        val current = leader
        if (current == null || (best != current && values[best] - values[current] >= config.margin)) leader = best

        val subjectIndices = if (subjectIds.isEmpty()) emptyList() else categories.indices.filter { categories[it].id in subjectIds }
        val bestSubject = subjectIndices.maxWithOrNull(compareBy<Int> { values[it] }.thenByDescending { it })
        streak = if (bestSubject != null && values[bestSubject] >= config.correctThreshold) streak + 1 else 0
        val correct = bestSubject != null && streak >= config.requiredStreak
        if (correct) leader = bestSubject  // 맞힌 범주를 1등으로 보여 줘요(설정에 따라 히스테리시스가 1등을 붙잡고 있을 수 있어서)

        var guessed = state.guessed
        var guessedAt = state.guessedAtMillis
        var elapsed = state.elapsedMillis
        if (correct && !guessed) {
            guessed = true
            guessedAt = atMillis
            elapsed = startedAtMillis?.let { (atMillis - it).coerceAtLeast(0L) }
        }

        val top = checkNotNull(leader)
        val status = when {
            correct -> LiveGuessStatus.Correct
            values[top] < config.unsureThreshold -> LiveGuessStatus.Unsure
            else -> LiveGuessStatus.Guessed
        }
        val order = listOf(top) + values.indices.filter { it != top }
            .sortedWith(compareByDescending<Int> { values[it] }.thenBy { it }).take(config.topCount - 1)
        val guesses = order.map { AiGuess(categories[it], rounded(values[it])) }
        state = LiveGuessState(status, guesses, guessed, guessedAt, elapsed)
        return state
    }

    /** 지우개로 지워서 잉크가 모자랄 때. 평활 기록은 버리지만 이미 맞혔다는 기록은 남겨요. */
    fun onEmpty(): LiveGuessState {
        smoothed = null
        leader = null
        streak = 0
        state = state.copy(status = LiveGuessStatus.TooEmpty, guesses = emptyList())
        return state
    }

    /** 새 그림을 시작하거나 전체를 지울 때. 맞힌 기록과 시작 시각까지 모두 지워요. */
    fun reset(): LiveGuessState {
        smoothed = null
        leader = null
        streak = 0
        startedAtMillis = null
        state = LiveGuessState()
        return state
    }

    private fun rounded(value: Float) = (value * 1000).roundToInt() / 1000f
}

/**
 * 추론을 언제 부를지 정해요(매 프레임 금지). 순수 로직이라 시각은 호출자가 넘겨요.
 * - 획이 끝나면 바로 허용해요.
 * - 그리는 중에는 마지막 추론을 시작한 뒤 [minIntervalMillis] 가 지나야 허용해요.
 * - 추론이 도는 중에 들어온 요청은 쌓지 않고 "하나 더 필요함"만 표시했다가, 끝나면 한 번만 다시 돌려요.
 *
 * 사용법: `if (gate.request(t, strokeEnded)) 추론 시작` → 성공·실패와 상관없이 끝나면 `if (gate.finish(t)) 다시 추론`.
 */
class InferenceGate(private val minIntervalMillis: Long = 500) {
    init { require(minIntervalMillis >= 0) { "minIntervalMillis 는 0 이상이어야 해요" } }

    var running = false
        private set
    var pending = false
        private set
    private var pendingStrokeEnd = false
    private var lastStartedAtMillis: Long? = null

    /** 지금 추론을 시작해도 되면 true(그때부터 실행 중으로 봐요). */
    fun request(atMillis: Long, strokeEnded: Boolean): Boolean {
        if (running) {
            pending = true
            pendingStrokeEnd = pendingStrokeEnd || strokeEnded
            return false
        }
        if (!strokeEnded && !intervalPassed(atMillis)) return false
        start(atMillis)
        return true
    }

    /**
     * 추론이 끝났을 때. 도는 동안 들어온 요청이 있으면 true — 바로 한 번 더 돌려 주세요(그 실행도 끝나면 다시 [finish]).
     * 그리는 중 요청만 있었고 아직 간격이 안 지났으면 버려요. 다음 움직임이나 획 끝에서 다시 요청이 와요.
     */
    fun finish(atMillis: Long): Boolean {
        running = false
        val rerun = pending && (pendingStrokeEnd || intervalPassed(atMillis))
        pending = false
        pendingStrokeEnd = false
        if (rerun) start(atMillis)
        return rerun
    }

    /** 새 그림·화면 이탈 때. 진행 중이던 추론의 결과는 호출자가 버려요(코루틴 취소 등). */
    fun reset() {
        running = false
        pending = false
        pendingStrokeEnd = false
        lastStartedAtMillis = null
    }

    private fun intervalPassed(atMillis: Long) = lastStartedAtMillis.let { it == null || atMillis - it >= minIntervalMillis }

    private fun start(atMillis: Long) {
        running = true
        lastStartedAtMillis = atMillis
    }
}
