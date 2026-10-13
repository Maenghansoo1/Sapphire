package com.sapphire.hawe.feature.mission

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.sapphire.hawe.core.data.SubmissionContent
import org.junit.Assert.*
import org.junit.Test

class TemplateInputRulesTest {
    @Test fun oneWordRemovesSpacesAndLimitsGraphemes() {
        assertEquals("오늘하루", oneWordInput(" 오늘 하루\n"))
        assertEquals("탭과줄바꿈", oneWordInput("탭과\t줄바꿈\r\n"))
        assertEquals("전각공백", oneWordInput("전각　공백"))  // 서버(Character.isWhitespace)도 공백으로 봐요
        assertEquals("가".repeat(20), oneWordInput("가".repeat(25)))
        assertEquals("😀".repeat(20), oneWordInput("😀".repeat(21)))  // 이모지 하나는 한 글자
    }

    @Test fun applyingKeepsCursorAfterSameCharacters() {
        // 에뮬레이터에서 "deep focus" 가 "deepfus" 로 된 문제: 공백을 지운 뒤 커서를 맞춰 줘야 다음 글자가 이어져요
        var value = TextFieldValue("deep", TextRange(4))
        "deep focus".drop(4).forEach { char ->
            val typed = value.text.substring(0, value.selection.end) + char + value.text.substring(value.selection.end)
            value = TextFieldValue(typed, TextRange(value.selection.end + 1)).applying(::oneWordInput)
        }
        assertEquals("deepfocus", value.text)
        assertEquals(TextRange(9), value.selection)
        // 가운데에 붙여 넣어도 커서는 붙여 넣은 글 바로 뒤
        assertEquals(TextFieldValue("abxycd", TextRange(4)),
            TextFieldValue("ab x y cd", TextRange(6)).applying(::oneWordInput))
        // 한도를 넘기면 잘린 끝에 커서
        assertEquals(TextFieldValue("가".repeat(20), TextRange(20)),
            TextFieldValue("가".repeat(21), TextRange(21)).applying(::oneWordInput))
        // 바뀐 게 없으면 한글 조합 중인 상태를 그대로 둬요
        val composing = TextFieldValue("하", TextRange(1), composition = TextRange(0, 1))
        assertSame(composing, composing.applying(::oneWordInput))
        assertEquals(TextFieldValue("한 줄", TextRange(2)), TextFieldValue("한\n줄", TextRange(2)).applying(::reviewInput))
    }

    @Test fun oneWordStateAndContent() {
        assertFalse(OneWordState().canSubmit)
        assertFalse(OneWordState().hasDraft)
        assertFalse(OneWordState(reason = "  ").hasDraft)
        val state = OneWordState("몰입", "  하루 종일 코딩  ")
        assertTrue(state.canSubmit)
        assertTrue(state.hasDraft)
        assertEquals("2 / 20", state.wordCounter)
        assertEquals("12 / 100", state.reasonCounter)  // 입력 중 글자 수는 앞뒤 공백 포함, 보낼 때만 지워요
        assertEquals(SubmissionContent.Scalar(text = "몰입", caption = "하루 종일 코딩"), state.content())
        assertEquals(SubmissionContent.Scalar(text = "몰입"), OneWordState("몰입", " \n ").content())
        assertTrue(OneWordState(reason = "이유만").let { it.hasDraft && !it.canSubmit })
    }

    @Test fun reasonLimitsCountCodePointsLikeServer() {
        assertEquals(3, codePointCount("가😀a"))
        assertEquals("가".repeat(100), reasonInput("가".repeat(150), TemplateLimits.ONE_WORD_REASON))
        // 서로게이트 쌍을 반으로 자르지 않아요
        assertEquals("😀".repeat(100), reasonInput("😀".repeat(101), TemplateLimits.ONE_WORD_REASON))
        assertEquals("짧은 이유", reasonInput("짧은 이유", TemplateLimits.BALANCE_REASON))
        assertEquals(200, codePointCount(reasonInput("나".repeat(300), TemplateLimits.BALANCE_REASON)))
        assertEquals("줄\n바꿈은 그대로", reasonInput("줄\n바꿈은 그대로", TemplateLimits.BALANCE_REASON))
    }

    @Test fun reviewIsOneLineWithStars() {
        assertEquals("한 줄 후기", reviewInput("한 줄\n후기"))
        assertEquals("윈도 줄바꿈", reviewInput("윈도\r\n줄바꿈"))
        assertEquals("가".repeat(100), reviewInput("가".repeat(120)))
        assertFalse(ReviewState().canSubmit)
        assertFalse(ReviewState(stars = 4).canSubmit)  // 후기 없음
        assertFalse(ReviewState(line = "맛있다").canSubmit)  // 별점 없음
        assertFalse(ReviewState(stars = 4, line = "   ").canSubmit)
        assertFalse(ReviewState(stars = 6, line = "맛있다").canSubmit)
        assertTrue(ReviewState(stars = 1, line = "맛있다").canSubmit)
        assertTrue(ReviewState(stars = 1).hasDraft)
        assertEquals("3 / 100", ReviewState(5, "맛있다").counter)
        assertEquals(SubmissionContent.Scalar(number = 5, text = "또 먹고 싶다"), ReviewState(5, " 또 먹고 싶다 ").content())
    }

    @Test fun balanceNeedsChoice() {
        assertFalse(BalanceState().canSubmit)
        assertFalse(BalanceState(reason = "이유").canSubmit)
        assertTrue(BalanceState(reason = "이유").hasDraft)
        assertFalse(BalanceState("C").canSubmit)
        assertTrue(BalanceState("A").canSubmit)
        assertEquals(SubmissionContent.Scalar(choice = "B"), BalanceState("B", "  ").content())
        assertEquals(SubmissionContent.Scalar(choice = "A", caption = "면이 좋아"), BalanceState("A", "면이 좋아\n").content())
        assertEquals("0 / 200", BalanceState().counter)
    }

    @Test fun sliderAlwaysHasIntegerValues() {
        assertEquals(50, SliderState().number)
        assertFalse(SliderState().touched)
        assertEquals(SubmissionContent.Scalar(number = 50), SliderState().content())
        assertEquals(SubmissionContent.Scalar(number = 80, guess = 60), SliderState(80, 60, consensus = true).content())
        assertEquals(SubmissionContent.Scalar(number = 80), SliderState(80, 60, consensus = false).content())
        assertEquals(0, sliderValue(-3f))
        assertEquals(100, sliderValue(100.4f))
        assertEquals(63, sliderValue(62.5f))
        assertEquals(62, sliderValue(62.49f))
    }
}
