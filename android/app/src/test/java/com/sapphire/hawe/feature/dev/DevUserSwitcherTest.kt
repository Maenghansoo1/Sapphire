package com.sapphire.hawe.feature.dev

import com.sapphire.hawe.core.net.DevUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DevUserSwitcherTest {
    private val one = DevUser(1, nickname = "개발자1")
    private val two = DevUser(2, nickname = "개발자2")
    private val three = DevUser(3, nickname = "개발자3")

    @Test fun twoUsersToggle() {
        assertEquals(2L, nextDevUser(listOf(one, two), 1)?.userId)
        assertEquals(1L, nextDevUser(listOf(one, two), 2)?.userId)
    }

    @Test fun moreUsersCycleAndWrapAround() {
        assertEquals(3L, nextDevUser(listOf(one, two, three), 2)?.userId)
        assertEquals(1L, nextDevUser(listOf(one, two, three), 3)?.userId)
    }

    @Test fun currentMissingFromListPicksFirstOther() {
        assertEquals(1L, nextDevUser(listOf(one, two), 99)?.userId)
    }

    @Test fun nobodyToSwitchTo() {
        assertNull(nextDevUser(emptyList(), 1))
        assertNull(nextDevUser(listOf(one), 1))
    }
}
