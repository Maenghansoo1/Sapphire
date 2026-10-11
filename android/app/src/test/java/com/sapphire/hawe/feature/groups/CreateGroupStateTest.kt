package com.sapphire.hawe.feature.groups

import com.sapphire.hawe.designsystem.assets.Relationship
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CreateGroupStateTest {
    private val today = LocalDate.of(2026, 10, 12)

    @Test fun defaultsAndNameLimit() {
        val state = CreateGroupState(selectedDate = today)
        assertEquals(Relationship.FRIEND, state.relationship)
        assertFalse(state.withName("  ").canCreate)
        assertTrue(state.withName("  우리  ").canCreate)
        assertEquals(16, state.withName("abcdefghijklmnopq").name.length)
        assertNull(state.startDateForCreation)
        assertEquals(6, state.maxMembers)
        assertEquals("만든 뒤 초대 코드로 최대 5명을 더 초대할 수 있어요.", state.subtitle)
    }

    @Test fun loverUsesDateAndTwoMembers() {
        val state = CreateGroupState("우리 둘", Relationship.LOVER, today)
        assertTrue(state.showsStartDate)
        assertEquals(today, state.startDateForCreation)
        assertEquals(2, state.maxMembers)
        assertEquals("만든 뒤 초대 코드로 연인 1명을 초대할 수 있어요.", state.subtitle)
        assertNull(state.copy(relationship = Relationship.FAMILY).startDateForCreation)
    }
}
