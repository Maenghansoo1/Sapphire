package com.sapphire.hawe.feature.mission

import org.junit.Assert.*
import org.junit.Test

class WriteMissionStateTest {
    @Test fun lengthAndPasteLimit() {
        assertEquals(300, WriteMissionState().withText("가".repeat(300)).count)
        assertEquals("가".repeat(300), WriteMissionState().withText("가".repeat(301)).text)
        assertEquals("a".repeat(300), WriteMissionState().withText("a".repeat(1000)).text)
        assertEquals(3, WriteMissionState("😀가a").count)
    }

    @Test fun blankAndTrimmedSubmission() {
        val blank = WriteMissionState("  \n\t ")
        assertFalse(blank.canSubmit)
        assertEquals("", blank.trimmed)
        assertEquals("0 / 300", WriteMissionState().counterText)
        assertEquals("12 / 300", WriteMissionState("a".repeat(12)).counterText)
        assertEquals("첫 줄\n둘째 줄", WriteMissionState("  첫 줄\n둘째 줄  ").trimmed)
    }

    @Test fun fillBlankUsesOneHundredGraphemeLimit() {
        val state = WriteMissionState(spec = WriteMissionSpec(maxLength = 100)).withText("가".repeat(101))
        assertEquals(100, state.count)
        assertEquals("100 / 100", state.counterText)
        assertTrue(state.canSubmit)
    }

    @Test fun acrosticRequiresThreeMatchingNonblankLines() {
        val spec = WriteMissionSpec(acrosticWord = "고양이")
        assertTrue(WriteMissionState("고마워\n양손 모아\n이제 시작", spec).canSubmit)
        assertEquals("3줄로 써 주세요", WriteMissionState("고마워\n양손", spec).acrosticError())
        assertEquals("‘양’로 시작해 주세요", WriteMissionState("고마워\n다른 줄\n이제", spec).acrosticError())
        assertFalse(WriteMissionState("고마워\n\n이제", spec).canSubmit)
        assertFalse(WriteMissionState("고마워\n양손\n이제\n네번째", spec).canSubmit)
        // 줄 사이·앞에 빈 줄이 있어도 3줄이면 올릴 수 있고, 빈 줄을 뺀 3줄로 보내요
        val spaced = WriteMissionState("\n고마워\n\n양손 모아\n   \n이제 시작\n", spec)
        assertTrue(spaced.canSubmit)
        assertEquals("고마워\n양손 모아\n이제 시작", spaced.submitText)
        assertEquals("그냥 글", WriteMissionState("  그냥 글 ").submitText)
    }
}
