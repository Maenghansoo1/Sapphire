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

    @Test fun templateSupportAndAggregateSets() {
        // 서버 1차 틀 10종(MISSIONS_API §1-2)은 모두 화면이 있어요
        listOf("PHOTO_NOW", "PHOTO_GALLERY", "PHOTO_COLOR", "TEXT_ACROSTIC", "TEXT_ONE_WORD", "TEXT_REVIEW",
            "TEXT_BALANCE", "TEXT_FILL_BLANK", "DRAW_SKETCH", "DRAW_A_TO_B").forEach { assertTrue(it, supportsTemplate(it)) }
        // 2차 틀·없앤 음성·슬라이더 틀·모르는 값은 "앱 업데이트" 안내
        listOf("TEXT_RELAY", "DRAW_GUESS", "SLIDER_POSITION", "SLIDER_TASTE", "SLIDER_CONSENSUS", "VOICE_LINE", "",
            "text_balance").forEach { assertFalse(it, supportsTemplate(it)) }
        assertEquals(setOf("TEXT_BALANCE"), MissionTemplates.supported.filter(::isAggregateTemplate).toSet())
        assertFalse(isAggregateTemplate("SLIDER_POSITION"))
    }

    @Test fun missionTypeParticlesFollowFinalConsonant() {
        assertEquals(listOf("그림이", "사진이", "글이"), MissionType.entries.map { it.subjectForm })
        assertEquals(listOf("그림을", "사진을", "글을"), MissionType.entries.map { it.objectForm })
    }
}
