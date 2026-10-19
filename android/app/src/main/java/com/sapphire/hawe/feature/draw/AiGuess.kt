package com.sapphire.hawe.feature.draw

import com.sapphire.hawe.core.josa
import kotlin.math.roundToInt

/**
 * "잘 모르겠어요" 기준. 1등 확률이 이보다 낮으면 답하지 않아요.
 * M1 6단계에서 팀원이 그림판으로 그린 249장(`ml/quickdraw/EVAL.md`)으로 정한 값 — 범주 밖 그림에 틀린 답을 하는 비율이
 * 0.35 때 44% → 28% 로 줄어요. 모델 설명서(`export.json` 의 `unsure_threshold`)와 같아야 해요(다르면 단위 테스트 실패).
 */
const val AI_GUESS_THRESHOLD = 0.55f

/** 주제가 2·3등이어도 확률이 이보다 낮으면 "아깝게"가 아니라 "못 맞혔어요"예요(0%짜리 3등을 아깝다고 하지 않게). */
const val AI_CLOSE_MIN_PROBABILITY = 0.15f

data class AiGuess(val category: DrawCategory, val probability: Float)

/** 확률이 높은 순서로 [count]개. 같은 확률이면 범주 번호 순. */
fun topGuesses(probabilities: FloatArray, categories: List<DrawCategory>, count: Int = 3): List<AiGuess> {
    require(probabilities.size == categories.size) { "모델 출력 수와 범주 수가 달라요" }
    return probabilities.indices.sortedWith(compareByDescending<Int> { probabilities[it] }.thenBy { it })
        .take(count).map { AiGuess(categories[it], probabilities[it]) }
}

/** AI 추측 결과. 결과는 저장하거나 서버로 보내지 않아요(재미용). */
sealed interface AiVerdict {
    /** 그림이 너무 비어서 추론하지 않음 */
    data object TooEmpty : AiVerdict
    /** 1등 확률이 기준 미만 */
    data class Unsure(val guesses: List<AiGuess>) : AiVerdict
    /** 주제를 범주로 알 수 없어 판정 없이 추측만 */
    data class Guessed(val guesses: List<AiGuess>) : AiVerdict
    /** 1등이 주제와 같음 */
    data class Correct(val guesses: List<AiGuess>) : AiVerdict
    /** 1등은 아니지만 2·3등 안에 주제가 있고 확률도 꽤 있음([AI_CLOSE_MIN_PROBABILITY] 이상) */
    data class Close(val guesses: List<AiGuess>, val subject: DrawCategory, val rank: Int) : AiVerdict
    /** 3등 안에 주제가 없거나 있어도 확률이 아주 낮음 */
    data class Missed(val guesses: List<AiGuess>, val subject: DrawCategory) : AiVerdict
}

fun aiVerdict(guesses: List<AiGuess>?, subject: DrawCategory?, threshold: Float = AI_GUESS_THRESHOLD): AiVerdict =
    aiVerdict(guesses, listOfNotNull(subject), threshold)

/** [subjects] 중 하나라도 맞히면 맞힌 걸로 쳐요(A를 B로는 A·B 둘 다). 첫 번째가 대표 주제예요. */
fun aiVerdict(guesses: List<AiGuess>?, subjects: List<DrawCategory>, threshold: Float = AI_GUESS_THRESHOLD): AiVerdict {
    if (guesses.isNullOrEmpty()) return AiVerdict.TooEmpty
    if (guesses.first().probability < threshold) return AiVerdict.Unsure(guesses)
    if (subjects.isEmpty()) return AiVerdict.Guessed(guesses)
    val rank = guesses.indexOfFirst { guess -> subjects.any { it.id == guess.category.id } }
    return when {
        rank == 0 -> AiVerdict.Correct(guesses)
        rank > 0 && guesses[rank].probability >= AI_CLOSE_MIN_PROBABILITY ->
            AiVerdict.Close(guesses, guesses[rank].category, rank + 1)
        else -> AiVerdict.Missed(guesses, subjects.first())
    }
}

/** 결과 카드 제목·설명. 그린 사람을 탓하지 않는 말투로(정확도가 낮을 때도). [mine] 은 내가 방금 그린 그림인지. */
data class AiVerdictCopy(val title: String, val detail: String)

fun aiVerdictCopy(verdict: AiVerdict, mine: Boolean): AiVerdictCopy = when (verdict) {
    AiVerdict.TooEmpty -> if (mine) AiVerdictCopy("조금 더 그려 볼까요?", "AI가 알아보기엔 아직 그림이 너무 작아요")
        else AiVerdictCopy("AI가 알아보기 어려워요", "그림이 너무 작거나 비어 있어요")
    is AiVerdict.Unsure -> AiVerdictCopy("잘 모르겠어요", if (mine) "AI가 아직 배우는 중이에요. 조금 더 그려 볼까요?"
        else "AI도 헷갈린대요. ‘${verdict.guesses.first().category.ko}’일까요?")
    is AiVerdict.Guessed -> AiVerdictCopy("AI는 ${quoted(verdict.guesses.first().category.ko, "이라고", "라고")} 생각해요",
        "AI가 고른 후보예요")
    is AiVerdict.Correct -> AiVerdictCopy("AI가 맞혔어요!", "‘${verdict.guesses.first().category.ko}’ 그림이 맞대요")
    is AiVerdict.Close -> AiVerdictCopy("아깝게 놓쳤어요", "${quoted(verdict.subject.ko, "을", "를")} ${verdict.rank}등으로 " +
        "골랐어요. AI는 ${quoted(verdict.guesses.first().category.ko, "이라고", "라고")} 생각했어요")
    is AiVerdict.Missed -> AiVerdictCopy("AI가 못 맞혔어요",
        "AI는 ${quoted(verdict.guesses.first().category.ko, "이라고", "라고")} 생각했어요")
}

/** ‘달’이라고, ‘해’라고 — 따옴표 안 낱말의 받침에 맞춘 조사 */
private fun quoted(word: String, withConsonant: String, withoutConsonant: String) =
    "‘$word’${josa(word, withConsonant, withoutConsonant)}"

fun AiVerdict.guessesOrEmpty(): List<AiGuess> = when (this) {
    AiVerdict.TooEmpty -> emptyList()
    is AiVerdict.Unsure -> guesses
    is AiVerdict.Guessed -> guesses
    is AiVerdict.Correct -> guesses
    is AiVerdict.Close -> guesses
    is AiVerdict.Missed -> guesses
}

/** 확률 막대 옆 숫자. 반올림해서 0~100%. */
fun percentText(probability: Float): String = "${(probability.coerceIn(0f, 1f) * 100).roundToInt()}%"
