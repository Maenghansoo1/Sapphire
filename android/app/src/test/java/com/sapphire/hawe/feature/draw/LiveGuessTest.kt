package com.sapphire.hawe.feature.draw

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class LiveGuessTest {
    private val categories = parseDrawCategories(File("src/main/assets/quickdraw/categories.json").readText())
    private fun category(ko: String) = categories.first { it.ko == ko }
    private fun probs(vararg pairs: Pair<String, Float>) =
        FloatArray(categories.size).also { values -> pairs.forEach { (ko, p) -> values[category(ko).id] = p } }
    /** 평활 없이 마지막 결과 그대로(히스테리시스·연속 판정만 보려고) */
    private val noSmoothing = LiveGuessConfig(alpha = 1f)

    private fun LiveGuessState.top() = guesses.first()

    @Test fun emaMovesTowardRepeatedResult() {
        val tracker = LiveGuessTracker(subjects = emptyList())
        val first = tracker.onInference(probs("고양이" to 0.2f), categories, 0)
        assertEquals(LiveGuessStatus.Unsure, first.status)
        assertEquals(0.2f, first.top().probability, 0.001f)
        val expected = listOf(0.6f, 0.8f, 0.9f)  // 0.5 씩 새 값(1.0) 쪽으로
        expected.forEachIndexed { index, value ->
            val state = tracker.onInference(probs("고양이" to 1f), categories, index + 1L)
            assertEquals(category("고양이"), state.top().category)
            assertEquals(value, state.top().probability, 0.002f)
            assertEquals(LiveGuessStatus.Guessed, state.status)
        }
    }

    @Test fun leaderChangesOnlyWhenNewTopIsAheadByMargin() {
        val tracker = LiveGuessTracker(noSmoothing, emptyList())
        assertEquals(category("고양이"), tracker.onInference(probs("고양이" to 0.5f, "강아지" to 0.4f), categories, 0).top().category)
        // 강아지가 앞섰지만 0.06 차이라 1등은 그대로(흔들림 방지). 2등에는 실제 확률대로 보여요
        val held = tracker.onInference(probs("고양이" to 0.42f, "강아지" to 0.48f), categories, 1)
        assertEquals(listOf("고양이", "강아지"), held.guesses.take(2).map { it.category.ko })
        assertEquals(0.48f, held.guesses[1].probability, 0.001f)
        // 0.3 차이면 바뀌어요
        assertEquals(category("강아지"), tracker.onInference(probs("고양이" to 0.3f, "강아지" to 0.6f), categories, 2).top().category)
        assertEquals(category("강아지"), tracker.onInference(probs("고양이" to 0.5f, "강아지" to 0.45f), categories, 3).top().category)
    }

    @Test fun showsTopThreeWithLeaderFirst() {
        val tracker = LiveGuessTracker(noSmoothing, emptyList())
        val state = tracker.onInference(probs("고양이" to 0.6f, "강아지" to 0.2f, "토끼" to 0.15f, "해" to 0.05f), categories, 0)
        assertEquals(listOf("고양이", "강아지", "토끼"), state.guesses.map { it.category.ko })
        assertEquals(1, LiveGuessTracker(LiveGuessConfig(topCount = 1), emptyList())
            .onInference(probs("고양이" to 0.6f, "강아지" to 0.4f), categories, 0).guesses.size)
    }

    @Test fun correctNeedsConsecutiveInferencesAboveThreshold() {
        val tracker = LiveGuessTracker(noSmoothing, listOf(category("고양이")))
        val once = tracker.onInference(probs("고양이" to 0.7f), categories, 1000)
        assertEquals(LiveGuessStatus.Guessed, once.status)
        assertFalse(once.guessed)
        val twice = tracker.onInference(probs("고양이" to 0.7f), categories, 2000)
        assertEquals(LiveGuessStatus.Correct, twice.status)
        assertTrue(twice.guessed)
        assertEquals(2000L, twice.guessedAtMillis)

        // 중간에 한 번 떨어지면 처음부터 다시 세요
        val broken = LiveGuessTracker(noSmoothing, listOf(category("고양이")))
        broken.onInference(probs("고양이" to 0.7f), categories, 0)
        broken.onInference(probs("고양이" to 0.4f), categories, 1)
        assertEquals(LiveGuessStatus.Guessed, broken.onInference(probs("고양이" to 0.7f), categories, 2).status)
        assertEquals(LiveGuessStatus.Correct, broken.onInference(probs("고양이" to 0.7f), categories, 3).status)
    }

    @Test fun thresholdBoundary() {
        val exact = LiveGuessTracker(noSmoothing, listOf(category("고양이")))
        repeat(2) { exact.onInference(probs("고양이" to LIVE_CORRECT_THRESHOLD), categories, it.toLong()) }
        assertEquals(LiveGuessStatus.Correct, exact.state.status)
        val below = LiveGuessTracker(noSmoothing, listOf(category("고양이")))
        repeat(5) { below.onInference(probs("고양이" to 0.59f), categories, it.toLong()) }
        assertEquals(LiveGuessStatus.Guessed, below.state.status)
        assertFalse(below.state.guessed)
    }

    @Test fun anyOfSeveralSubjectsCounts() {
        // A를 B로는 a·b 둘 다 맞힌 걸로 쳐요(DrawSubjectMatcher.subjects)
        val tracker = LiveGuessTracker(noSmoothing, listOf(category("고양이"), category("토끼")))
        repeat(2) { tracker.onInference(probs("토끼" to 0.8f), categories, it.toLong()) }
        assertEquals(LiveGuessStatus.Correct, tracker.state.status)
        assertEquals(category("토끼"), tracker.state.top().category)
    }

    @Test fun correctSubjectIsShownFirstEvenIfHysteresisHeldAnotherLeader() {
        val tracker = LiveGuessTracker(LiveGuessConfig(alpha = 1f, margin = 0.5f), listOf(category("고양이")))
        tracker.onInference(probs("강아지" to 0.5f, "고양이" to 0.3f), categories, 0)
        assertEquals(category("강아지"), tracker.onInference(probs("고양이" to 0.65f, "강아지" to 0.3f), categories, 1).top().category)
        val correct = tracker.onInference(probs("고양이" to 0.65f, "강아지" to 0.3f), categories, 2)
        assertEquals(LiveGuessStatus.Correct, correct.status)
        assertEquals(category("고양이"), correct.top().category)
    }

    @Test fun withoutSubjectsOnlyGuesses() {
        // 주제를 범주로 모르거나 [대상] 미션이면 subjects 가 비어 있어요
        val tracker = LiveGuessTracker(noSmoothing, emptyList())
        repeat(3) { tracker.onInference(probs("고양이" to 0.95f), categories, it.toLong()) }
        assertEquals(LiveGuessStatus.Guessed, tracker.state.status)
        assertFalse(tracker.state.guessed)
        assertNull(tracker.state.guessedAtMillis)
        assertEquals(LiveGuessStatus.Unsure, tracker.onInference(probs("고양이" to 0.3f, "강아지" to 0.2f), categories, 9).status)
    }

    @Test fun firstGuessTimeStaysFixedUntilReset() {
        var clock = 0L
        val tracker = LiveGuessTracker(noSmoothing, listOf(category("고양이"))) { clock }
        clock = 1000; tracker.onStrokeStart()
        clock = 1500; tracker.onStrokeStart()  // 두 번째 획은 시작 시각을 바꾸지 않아요
        clock = 2000; tracker.onInference(probs("고양이" to 0.8f), categories)
        clock = 3500; val correct = tracker.onInference(probs("고양이" to 0.8f), categories)
        assertEquals(3500L, correct.guessedAtMillis)
        assertEquals(2500L, correct.elapsedMillis)

        clock = 5000; assertEquals(3500L, tracker.onInference(probs("고양이" to 0.95f), categories).guessedAtMillis)
        // 다른 걸로 바뀌어도 맞힌 기록은 남아요
        clock = 6000; val changed = tracker.onInference(probs("강아지" to 0.9f, "고양이" to 0.05f), categories)
        assertEquals(LiveGuessStatus.Guessed, changed.status)
        assertEquals(category("강아지"), changed.top().category)
        assertTrue(changed.guessed)
        assertEquals(3500L, changed.guessedAtMillis)
        assertEquals(2500L, changed.elapsedMillis)
        // 지우개로 다 지워도 남아요
        val empty = tracker.onEmpty()
        assertEquals(LiveGuessStatus.TooEmpty, empty.status)
        assertTrue(empty.guesses.isEmpty())
        assertTrue(empty.guessed)
        assertEquals(3500L, empty.guessedAtMillis)
        // 새 그림에서만 지워요
        assertEquals(LiveGuessState(), tracker.reset())
        tracker.onStrokeStart(10_000)
        tracker.onInference(probs("고양이" to 0.8f), categories, 12_000)
        assertEquals(3000L, tracker.onInference(probs("고양이" to 0.8f), categories, 13_000).elapsedMillis)
    }

    @Test fun elapsedIsUnknownWithoutStrokeStart() {
        val tracker = LiveGuessTracker(noSmoothing, listOf(category("고양이")))
        repeat(2) { tracker.onInference(probs("고양이" to 0.8f), categories, 100L + it) }
        assertTrue(tracker.state.guessed)
        assertNull(tracker.state.elapsedMillis)
    }

    @Test fun emptyClearsSmoothing() {
        val tracker = LiveGuessTracker(subjects = emptyList())
        tracker.onInference(probs("고양이" to 0.9f), categories, 0)
        tracker.onEmpty()
        // 지운 뒤 새 결과는 예전 확률과 섞이지 않아요
        val fresh = tracker.onInference(probs("강아지" to 0.7f), categories, 1)
        assertEquals(category("강아지"), fresh.top().category)
        assertEquals(0.7f, fresh.top().probability, 0.001f)
    }

    @Test fun sameInputGivesEqualState() {
        val tracker = LiveGuessTracker(subjects = emptyList())
        val input = probs("고양이" to 0.7f, "강아지" to 0.2f, "토끼" to 0.1f)
        val first = tracker.onInference(input, categories, 0)
        assertEquals(first, tracker.onInference(input, categories, 1))
        assertEquals(first, tracker.onInference(input, categories, 2))

        val judged = LiveGuessTracker(subjects = listOf(category("고양이")))
        judged.onInference(input, categories, 0)
        val correct = judged.onInference(input, categories, 1)
        assertEquals(LiveGuessStatus.Correct, correct.status)
        assertEquals(correct, judged.onInference(input, categories, 2))
    }

    @Test fun badInputIsIgnoredWithoutCrashing() {
        val tracker = LiveGuessTracker(subjects = emptyList())
        val state = tracker.onInference(probs("고양이" to 0.8f), categories, 0)
        assertSame(state, tracker.onInference(FloatArray(3), categories, 1))
        assertSame(state, tracker.onInference(probs("고양이" to Float.NaN), categories, 2))
        assertSame(state, tracker.onInference(probs("고양이" to Float.POSITIVE_INFINITY), categories, 3))
        assertSame(state, tracker.onInference(floatArrayOf(), emptyList(), 4))
        assertEquals(state, tracker.state)
        // 1을 살짝 넘거나 0 아래인 값은 범위 안으로 잘라요
        val clamped = LiveGuessTracker(noSmoothing, emptyList()).onInference(probs("고양이" to 1.2f, "강아지" to -0.1f), categories, 0)
        assertEquals(1f, clamped.top().probability, 0f)
        assertTrue(clamped.guesses.all { it.probability in 0f..1f })
    }

    @Test fun pendingShowsThinkingOnlyBeforeFirstGuess() {
        val tracker = LiveGuessTracker(subjects = emptyList())
        assertEquals(LiveGuessStatus.TooEmpty, tracker.state.status)
        assertEquals(LiveGuessStatus.Thinking, tracker.onInkPending().status)
        val guessed = tracker.onInference(probs("고양이" to 0.8f), categories, 0)
        assertSame(guessed, tracker.onInkPending())  // 이미 추측이 있으면 깜빡이지 않게 그대로
        tracker.onEmpty()
        assertEquals(LiveGuessStatus.Thinking, tracker.onInkPending().status)
    }

    @Test fun invalidConfigIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { LiveGuessConfig(alpha = 0f) }
        assertThrows(IllegalArgumentException::class.java) { LiveGuessConfig(alpha = 1.5f) }
        assertThrows(IllegalArgumentException::class.java) { LiveGuessConfig(requiredStreak = 0) }
        assertThrows(IllegalArgumentException::class.java) { LiveGuessConfig(margin = -0.1f) }
        assertThrows(IllegalArgumentException::class.java) { LiveGuessConfig(topCount = 0) }
        assertThrows(IllegalArgumentException::class.java) { InferenceGate(-1) }
    }

    @Test fun gateLimitsCallsWhileDrawing() {
        val gate = InferenceGate(500)
        assertTrue(gate.request(0, strokeEnded = false))  // 첫 요청은 바로
        assertFalse(gate.finish(10))
        assertFalse(gate.request(400, strokeEnded = false))  // 500ms 안이라 거부
        assertFalse(gate.running)
        assertTrue(gate.request(500, strokeEnded = false))
        assertFalse(gate.finish(510))
        assertTrue(gate.request(520, strokeEnded = true))  // 획이 끝나면 간격과 상관없이 바로
        assertFalse(gate.finish(530))
    }

    @Test fun gateRunsOnceMoreAfterRequestsDuringInference() {
        val gate = InferenceGate(500)
        assertTrue(gate.request(0, strokeEnded = true))
        repeat(5) { assertFalse(gate.request(10L + it, strokeEnded = it == 2)) }
        assertTrue(gate.pending)
        assertTrue(gate.finish(50))  // 쌓인 요청 여러 개 → 한 번만 다시
        assertTrue(gate.running)
        assertFalse(gate.pending)
        assertFalse(gate.finish(60))
        assertFalse(gate.running)
    }

    @Test fun gateDropsPendingMidStrokeRequestUntilIntervalPasses() {
        val gate = InferenceGate(500)
        assertTrue(gate.request(0, strokeEnded = false))
        assertFalse(gate.request(100, strokeEnded = false))
        assertFalse(gate.finish(200))  // 그리는 중 요청이고 아직 500ms 전 → 버림
        assertFalse(gate.pending)

        assertTrue(gate.request(1000, strokeEnded = false))
        assertFalse(gate.request(1100, strokeEnded = false))
        assertTrue(gate.finish(1600))  // 추론이 오래 걸려 간격이 지났으면 다시
    }

    @Test fun gateResetClearsEverything() {
        val gate = InferenceGate(500)
        assertTrue(gate.request(0, strokeEnded = false))
        gate.request(10, strokeEnded = true)
        gate.reset()
        assertFalse(gate.running)
        assertFalse(gate.pending)
        assertTrue(gate.request(20, strokeEnded = false))  // 간격 기록도 지워져서 바로
    }
}
