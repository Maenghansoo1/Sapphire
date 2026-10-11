package com.sapphire.hawe.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class InviteCodeTest {
    @Test fun sanitizesAsciiAndLimitsLength() {
        assertEquals("K7Q2PX", InviteCode.sanitize("k7q2px"))
        assertEquals("K7Q2PX", InviteCode.sanitize("k7-q2 px99"))
        assertEquals("ABC123", InviteCode.sanitize("한글abc123!"))
    }
}
