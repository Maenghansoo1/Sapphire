package com.sapphire.hawe.core.model

import com.sapphire.hawe.designsystem.assets.ProfileCharacter
import com.sapphire.hawe.designsystem.assets.Relationship
import org.junit.Assert.*
import org.junit.Test

class ServerMappingsTest {
    @Test fun relationshipsRoundTripAndUnknownFallsBack() {
        Relationship.entries.forEach { assertEquals(it, Relationship.fromServer(it.serverValue)) }
        assertEquals("COUPLE", Relationship.LOVER.serverValue)
        assertEquals("CASUAL", Relationship.SOSO.serverValue)
        assertEquals(Relationship.SOSO, Relationship.fromServer("OTHER"))
        assertEquals(Relationship.SOSO, Relationship.fromServer(null))
    }

    @Test fun characterKeysUseDefaultForMissingValues() {
        ProfileCharacter.entries.forEach { assertEquals(it, ProfileCharacter.fromKey(it.value)) }
        assertEquals(ProfileCharacter.DEFAULT, ProfileCharacter.fromKey(null))
        assertEquals(ProfileCharacter.DEFAULT, ProfileCharacter.fromKey("unknown"))
    }
}
