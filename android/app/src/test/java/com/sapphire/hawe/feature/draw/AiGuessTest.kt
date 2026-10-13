package com.sapphire.hawe.feature.draw

import com.sapphire.hawe.core.model.MissionSpec
import com.sapphire.hawe.core.model.MissionTemplates
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AiGuessTest {
    private val categories = parseDrawCategories(File("src/main/assets/quickdraw/categories.json").readText())
    private fun category(ko: String) = categories.first { it.ko == ko }
    private fun guesses(vararg pairs: Pair<String, Float>) = pairs.map { (ko, p) -> AiGuess(category(ko), p) }

    @Test fun categoriesLoadInModelOrder() {
        assertEquals(50, categories.size)
        assertEquals("해", categories[0].ko)
        assertEquals(listOf("해", "태양"), categories[0].names)
        assertThrows(IllegalArgumentException::class.java) {
            parseDrawCategories("""[{"id":1,"quickdraw":"moon","ko":"달"}]""")  // 0번이 없음
        }
    }

    @Test fun topGuessesSortByProbabilityThenId() {
        val probabilities = FloatArray(50).also { it[12] = 0.6f; it[13] = 0.2f; it[14] = 0.2f; it[0] = 0.0f }
        val top = topGuesses(probabilities, categories)
        assertEquals(listOf("고양이", "강아지", "토끼"), top.map { it.category.ko })
        assertEquals(0.6f, top[0].probability)
        assertThrows(IllegalArgumentException::class.java) { topGuesses(FloatArray(3), categories) }
    }

    @Test fun verdicts() {
        val cat = category("고양이")
        assertEquals(AiVerdict.TooEmpty, aiVerdict(null, cat))
        val unsure = guesses("고양이" to 0.30f, "강아지" to 0.25f, "토끼" to 0.1f)
        assertEquals(AiVerdict.Unsure(unsure), aiVerdict(unsure, cat))
        val catFirst = guesses("고양이" to 0.8f, "강아지" to 0.1f, "토끼" to 0.05f)
        assertEquals(AiVerdict.Correct(catFirst), aiVerdict(catFirst, cat))
        assertEquals(AiVerdict.Guessed(catFirst), aiVerdict(catFirst, null))
        val dogFirst = guesses("강아지" to 0.5f, "고양이" to 0.3f, "토끼" to 0.1f)
        assertEquals(AiVerdict.Close(dogFirst, cat, 2), aiVerdict(dogFirst, cat))
        assertEquals(AiVerdict.Missed(dogFirst, category("해")), aiVerdict(dogFirst, category("해")))
        // 주제가 3등이어도 0%에 가까우면 "아깝게"가 아니라 못 맞힘(에뮬레이터에서 고양이 그림에 '해' 0%가 3등으로 나왔던 경우)
        val catDrawing = guesses("고양이" to 0.99f, "토끼" to 0.004f, "해" to 0.001f)
        assertEquals(AiVerdict.Missed(catDrawing, category("해")), aiVerdict(catDrawing, category("해")))
        assertTrue(aiVerdict(guesses("강아지" to 0.6f, "고양이" to 0.15f), cat) is AiVerdict.Close)
        // 기준 경계: 0.35 와 같으면 답해요
        assertTrue(aiVerdict(guesses("고양이" to 0.35f), cat) is AiVerdict.Correct)
    }

    @Test fun copyUsesParticlesAndNeverBlames() {
        val dog = guesses("강아지" to 0.5f, "고양이" to 0.3f, "토끼" to 0.1f)
        val moon = guesses("달" to 0.9f, "해" to 0.05f, "별" to 0.01f)
        assertEquals(AiVerdictCopy("AI가 맞혔어요!", "‘달’ 그림이 맞대요"), aiVerdictCopy(AiVerdict.Correct(moon), true))
        assertEquals("AI는 ‘달’이라고 생각해요", aiVerdictCopy(AiVerdict.Guessed(moon), true).title)
        assertEquals("AI는 ‘강아지’라고 생각해요", aiVerdictCopy(AiVerdict.Guessed(dog), false).title)
        assertEquals(AiVerdictCopy("아깝게 놓쳤어요", "‘고양이’를 2등으로 골랐어요. AI는 ‘강아지’라고 생각했어요"),
            aiVerdictCopy(AiVerdict.Close(dog, category("고양이"), 2), true))
        assertEquals("‘달’을 2등으로 골랐어요. AI는 ‘해’라고 생각했어요",
            aiVerdictCopy(AiVerdict.Close(guesses("해" to 0.5f, "달" to 0.4f), category("달"), 2), true).detail)
        assertEquals("AI는 ‘강아지’라고 생각했어요", aiVerdictCopy(AiVerdict.Missed(dog, category("해")), true).detail)
        assertEquals("조금 더 그려 볼까요?", aiVerdictCopy(AiVerdict.TooEmpty, true).title)
        assertEquals("AI가 알아보기 어려워요", aiVerdictCopy(AiVerdict.TooEmpty, false).title)
        assertEquals("잘 모르겠어요", aiVerdictCopy(AiVerdict.Unsure(dog), true).title)
        assertEquals("AI도 헷갈린대요. ‘강아지’일까요?", aiVerdictCopy(AiVerdict.Unsure(dog), false).detail)
        assertEquals(dog, AiVerdict.Missed(dog, category("해")).guessesOrEmpty())
        assertEquals(emptyList<AiGuess>(), AiVerdict.TooEmpty.guessesOrEmpty())
    }

    @Test fun percent() {
        assertEquals("73%", percentText(0.734f))
        assertEquals("100%", percentText(1.2f))
        assertEquals("0%", percentText(-0.1f))
    }

    @Test fun subjectMatching() {
        fun match(template: String, topic: String, spec: MissionSpec = MissionSpec()) =
            DrawSubjectMatcher.match(template, topic, spec, categories)?.ko
        // 빈칸 subject 가 먼저, 없으면 주제 문장
        assertEquals("고양이", match(MissionTemplates.DRAW_SKETCH, "아무거나", MissionSpec(subject = "고양이")))
        assertEquals("고양이", match(MissionTemplates.DRAW_SKETCH, "30초 안에 고양이를 그려줘"))
        // 별칭·띄어쓰기·더 긴 이름 우선
        assertEquals("해", match(MissionTemplates.DRAW_SKETCH, "", MissionSpec(subject = "태양")))
        assertEquals("생일 케이크", match(MissionTemplates.DRAW_SKETCH, "", MissionSpec(subject = "생일케이크")))
        assertEquals("생일 케이크", match(MissionTemplates.DRAW_SKETCH, "", MissionSpec(subject = "케이크")))
        // 한 글자 이름은 낱말이 통째로 같을 때만(조사는 떼고 봐요)
        assertEquals("달", match(MissionTemplates.DRAW_SKETCH, "오늘 밤 달을 그려줘"))
        assertNull(match(MissionTemplates.DRAW_SKETCH, "해적선을 그려줘"))  // '해' 아님
        assertNull(match(MissionTemplates.DRAW_SKETCH, "개구리를 그려줘"))  // '개'(강아지) 아님
        // A를 B로는 결과물 b 로 판정
        assertEquals("화분", match(MissionTemplates.DRAW_A_TO_B, "컵을 화분으로 바꿔 그려줘",
            MissionSpec(drawFrom = "컵", drawTo = "화분")))
        // 사람을 그리는 미션은 판정하지 않아요
        assertNull(match(MissionTemplates.DRAW_SKETCH, "잎사귀를 30초 안에 그려줘", MissionSpec(hasTarget = true)))
        // A를 B로에서 a 가 사람이어도 결과물(b)은 판정
        assertEquals("고양이", match(MissionTemplates.DRAW_A_TO_B, "잎사귀를 고양이로", MissionSpec(drawTo = "고양이", hasTarget = true)))
        assertNull(match(MissionTemplates.DRAW_SKETCH, "오늘 기분을 날씨로 그려줘"))
        assertNull(match(MissionTemplates.DRAW_SKETCH, ""))
    }
}
