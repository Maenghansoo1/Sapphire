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
}
