package com.sapphire.hawe.feature.mission

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.sapphire.hawe.core.data.SubmissionContent
import kotlin.math.roundToInt

/** 새 틀 입력 한도. 서버(`MISSIONS_API.md` §3-2, B5)와 같은 값이에요. 글은 grapheme, 이유(caption)는 코드포인트로 세요. */
object TemplateLimits {
    const val ONE_WORD = 20
    const val ONE_WORD_REASON = 100
    const val REVIEW = 100
    const val BALANCE_REASON = 200
    const val STARS = 5
    const val SLIDER_MAX = 100
    const val SLIDER_DEFAULT = 50
}

fun codePointCount(text: String): Int = text.codePointCount(0, text.length)

fun limitCodePoints(text: String, limit: Int): String =
    if (codePointCount(text) <= limit) text else text.substring(0, text.offsetByCodePoints(0, limit))

/** 한 단어: 띄어쓰기·줄바꿈은 입력하는 순간 지워요(서버는 단어 안의 공백을 거부해요). */
fun oneWordInput(input: String): String =
    limitGraphemes(input.filterNot { it.isWhitespace() }, TemplateLimits.ONE_WORD)

/** 한 줄 후기: 줄바꿈은 띄어쓰기로 바꿔요. */
fun reviewInput(input: String): String =
    limitGraphemes(input.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' '), TemplateLimits.REVIEW)

fun reasonInput(input: String, limit: Int): String = limitCodePoints(input, limit)

/**
 * 입력 규칙([transform])을 적용하고 커서를 같은 글자 뒤에 둬요. 글만 바꾸고 커서를 그대로 두면 키보드가 아는
 * 위치와 어긋나 뒤에 친 글자가 사라져요(예: "deep focus" → "deepfus"). 바뀐 게 없으면 한글 조합 상태를 그대로 둬요.
 */
fun TextFieldValue.applying(transform: (String) -> String): TextFieldValue {
    val next = transform(text)
    if (next == text) return this
    val cursor = transform(text.substring(0, selection.end.coerceIn(0, text.length))).length
    return TextFieldValue(next, TextRange(cursor.coerceAtMost(next.length)))
}

/** 비었거나 공백뿐인 이유는 보내지 않아요. */
internal fun optionalCaption(reason: String): String? = reason.trim().ifEmpty { null }

/** 슬라이더 값은 0~100 정수(서버는 소수를 거부해요). */
fun sliderValue(raw: Float): Int = raw.roundToInt().coerceIn(0, TemplateLimits.SLIDER_MAX)

data class OneWordState(val word: String = "", val reason: String = "") {
    val canSubmit: Boolean get() = word.isNotEmpty()
    val hasDraft: Boolean get() = word.isNotEmpty() || reason.isNotBlank()
    val wordCounter: String get() = "${graphemeCount(word)} / ${TemplateLimits.ONE_WORD}"
    val reasonCounter: String get() = "${codePointCount(reason)} / ${TemplateLimits.ONE_WORD_REASON}"
    fun content() = SubmissionContent.Scalar(text = word, caption = optionalCaption(reason))
}

data class ReviewState(val stars: Int = 0, val line: String = "") {
    val canSubmit: Boolean get() = stars in 1..TemplateLimits.STARS && line.isNotBlank()
    val hasDraft: Boolean get() = stars > 0 || line.isNotBlank()
    val counter: String get() = "${graphemeCount(line)} / ${TemplateLimits.REVIEW}"
    fun content() = SubmissionContent.Scalar(number = stars, text = line.trim())
}

data class BalanceState(val choice: String? = null, val reason: String = "") {
    val canSubmit: Boolean get() = choice == "A" || choice == "B"
    val hasDraft: Boolean get() = choice != null || reason.isNotBlank()
    val counter: String get() = "${codePointCount(reason)} / ${TemplateLimits.BALANCE_REASON}"
    fun content() = SubmissionContent.Scalar(choice = choice, caption = optionalCaption(reason))
}

/** 슬라이더는 늘 값이 있어서(기본 50) 바로 올릴 수 있어요. 다수결 예측은 내 위치와 평균 예측 두 값. */
data class SliderState(val number: Int = TemplateLimits.SLIDER_DEFAULT, val guess: Int = TemplateLimits.SLIDER_DEFAULT,
    val consensus: Boolean = false, val touched: Boolean = false) {
    fun content() = SubmissionContent.Scalar(number = number, guess = guess.takeIf { consensus })
}
