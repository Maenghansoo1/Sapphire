package com.sapphire.hawe.feature.groups

import com.sapphire.hawe.core.data.InviteLookupError
import com.sapphire.hawe.core.data.InviteLookupResult
import com.sapphire.hawe.core.model.GroupInvitePreview
import com.sapphire.hawe.core.model.Profile
import com.sapphire.hawe.designsystem.assets.Relationship
import org.junit.Assert.*
import org.junit.Test

class JoinGroupStateTest {
    private val preview = GroupInvitePreview("컴공 동기", Relationship.FRIEND, List(4) { Profile.mockMine.avatar })
    private val success = InviteLookupResult.Success(preview)

    @Test fun normalizesCodeAndClearsShortInput() {
        assertEquals("K7Q2PX", JoinGroupState().withCode("k7q2px").code)
        assertEquals("K7Q2PX", JoinGroupState().withCode("k7-q2 px99").code)
        assertEquals("K7Q2PX", JoinGroupState().withCode("한k7q2px").code)
        val short = JoinGroupState().withCode("K7Q2PX").withCode("K7Q2P")
        assertNull(short.preview)
        assertNull(short.errorMessage)
    }

    @Test fun successAndErrors() {
        assertEquals(preview, JoinGroupState().withCode("K7Q2PX").withResult("K7Q2PX", success).preview)
        for (error in InviteLookupError.entries) {
            val state = JoinGroupState().withCode("ZZZZZZ").withResult("ZZZZZZ", InviteLookupResult.Failure(error))
            assertEquals(error.message, state.errorMessage)
            assertNull(state.preview)
        }
        val pending = JoinGroupState().withCode("ABCDEF").withCode("ABCDE")
        assertSame(pending, pending.withResult("ABCDEF", success))
    }

    @Test fun initialCodeOnlyFillsEmptyState() {
        assertEquals("K7Q2PX", JoinGroupState().withInitialCode("k7q2px").code)
        val existing = JoinGroupState(code = "AB")
        assertSame(existing, existing.withInitialCode("k7q2px"))
    }
}
