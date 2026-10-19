package com.sapphire.hawe.core.dev

import org.junit.Assert.*
import org.junit.Test

class DevUnlockTest {
    @Test fun tenQuickTapsUnlockAndProgressStartsAtFive() {
        val unlock = DevUnlock()
        repeat(4) { assertNull(unlock.registerTap(it * 100L, false).message) }
        assertEquals("개발자 모드까지 5번", unlock.registerTap(400, false).message)
        repeat(4) { assertFalse(unlock.registerTap(500 + it * 100L, false).unlocked) }
        assertTrue(unlock.registerTap(900, false).unlocked)
        assertEquals("이미 개발자 모드예요", unlock.registerTap(1000, true).message)
    }

    @Test fun gapOverThreeSecondsRestartsCount() {
        val unlock = DevUnlock()
        repeat(9) { unlock.registerTap(it * 100L, false) }
        assertEquals(1, unlock.registerTap(3901, false).count)
    }
}
