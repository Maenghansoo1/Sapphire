package com.sapphire.hawe.feature.mypage

import org.junit.Assert.*
import org.junit.Test

class AvatarPhotoTests {
    @Test fun compressesOnlyAboveEightMegabytes() {
        assertFalse(shouldCompressAvatar(8L * 1024 * 1024))
        assertTrue(shouldCompressAvatar(8L * 1024 * 1024 + 1))
    }
}
