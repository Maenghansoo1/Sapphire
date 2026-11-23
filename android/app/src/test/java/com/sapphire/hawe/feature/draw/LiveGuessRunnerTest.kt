package com.sapphire.hawe.feature.draw

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** 그리는 중 말풍선 문구(LiveGuessRunner.kt 의 순수 부분). 화면 연결은 실기기에서 확인해요. */
class LiveGuessRunnerTest {
    private val categories = parseDrawCategories(File("src/main/assets/quickdraw/categories.json").readText())
    private fun guesses(vararg pairs: Pair<String, Float>) = pairs.map { (ko, p) -> AiGuess(categories.first { it.ko == ko }, p) }

    @Test fun emptyAndThinking() {
        val empty = liveGuessCopy(LiveGuessState())
        assertEquals("그리면 별이가 맞혀 볼게요", empty.title)
        assertEquals("그리는 동안 계속 추측해요", empty.detail)
        assertTrue(empty.candidates.isEmpty())
        assertNull(empty.badge)
        assertFalse(empty.celebrate)
        assertEquals("별이가 지켜보는 중…", liveGuessCopy(LiveGuessState(LiveGuessStatus.Thinking)).title)
    }

    @Test fun unsureShowsCandidatesWithoutBlame() {
        val copy = liveGuessCopy(LiveGuessState(LiveGuessStatus.Unsure, guesses("고양이" to 0.31f, "강아지" to 0.24f, "토끼" to 0.1f)))
        assertEquals("음… 아직 모르겠어요", copy.title)
        assertEquals(listOf("고양이", "강아지"), copy.candidates.map { it.category.ko })  // 상위 2개만 칩으로
        assertEquals("음… 아직 모르겠어요, 고양이 31%, 강아지 24%", copy.description)
    }

    @Test fun guessedSaysWhatByeolThinks() {
        val state = LiveGuessState(LiveGuessStatus.Guessed, guesses("강아지" to 0.7f, "고양이" to 0.2f))
        assertEquals("별이는 ‘강아지’라고 생각해요", liveGuessCopy(state).title)
        // 받침에 맞춘 조사
        val moon = LiveGuessState(LiveGuessStatus.Guessed, guesses("달" to 0.8f))
        assertEquals("별이는 ‘달’이라고 생각해요", liveGuessCopy(moon).title)
        assertEquals(listOf("강아지", "고양이"), liveGuessCopy(state).candidates.map { it.category.ko })
    }

    @Test fun correctCelebratesWithElapsedTime() {
        val state = LiveGuessState(LiveGuessStatus.Correct, guesses("고양이" to 0.8f), guessed = true,
            guessedAtMillis = 5000, elapsedMillis = 3400)
        val copy = liveGuessCopy(state)
        assertEquals("별이가 맞혔어요!", copy.title)
        assertEquals("‘고양이’ · 3초 만에", copy.detail)
        assertTrue(copy.candidates.isEmpty())
        assertNull(copy.badge)
        assertTrue(copy.celebrate)
        assertEquals("별이가 맞혔어요!, ‘고양이’ · 3초 만에", copy.description)
        assertEquals("‘고양이’", liveGuessCopy(state.copy(elapsedMillis = null)).detail)
    }

    @Test fun badgeKeepsEarlierHitAfterDrawingChanges() {
        val changed = LiveGuessState(LiveGuessStatus.Guessed, guesses("강아지" to 0.7f), guessed = true,
            guessedAtMillis = 5000, elapsedMillis = 2600)
        val copy = liveGuessCopy(changed)
        assertEquals("3초 만에 맞혔어요", copy.badge)
        assertFalse(copy.celebrate)
        // 지우개로 다 지워도 남아요
        assertEquals("3초 만에 맞혔어요", liveGuessCopy(changed.copy(status = LiveGuessStatus.TooEmpty, guesses = emptyList())).badge)
        assertEquals("맞혔어요", liveGuessCopy(changed.copy(elapsedMillis = null)).badge)
    }

    @Test fun elapsedRoundsToWholeSecondsAtLeastOne() {
        assertEquals(1L, elapsedSeconds(0))
        assertEquals(1L, elapsedSeconds(400))
        assertEquals(1L, elapsedSeconds(1499))
        assertEquals(2L, elapsedSeconds(1500))
        assertEquals(12L, elapsedSeconds(12_345))
        assertEquals(1L, elapsedSeconds(-50))
    }

    @Test fun trackerStatesMapToCopy() {
        // A1 상태 머신이 실제로 내는 상태를 그대로 넣어도 문구가 나와요
        val tracker = LiveGuessTracker(LiveGuessConfig(alpha = 1f), listOf(categories.first { it.ko == "고양이" }))
        val probabilities = FloatArray(categories.size).also { it[categories.first { c -> c.ko == "고양이" }.id] = 0.9f }
        tracker.onStrokeStart(0)
        tracker.onInference(probabilities, categories, 1000)
        assertEquals("별이는 ‘고양이’라고 생각해요", liveGuessCopy(tracker.state).title)
        tracker.onInference(probabilities, categories, 2000)
        assertEquals("‘고양이’ · 2초 만에", liveGuessCopy(tracker.state).detail)
    }
}
