package com.sapphire.hawe.feature.groups

import com.sapphire.hawe.core.model.AvatarContent
import com.sapphire.hawe.core.model.GroupMember
import com.sapphire.hawe.core.model.Profile
import com.sapphire.hawe.designsystem.assets.ProfileCharacter
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class GroupProfileStateTest {
    private val member = GroupMember(1L, Profile.mockMine, isMe = true)

    @Test fun defaultProfileSavesNull() {
        val state = GroupProfileState.from(member)
        assertTrue(state.useDefault)
        assertTrue(state.canSave)
        assertNull(state.profileToSave)
        assertEquals(member.defaultProfile, state.preview)
    }

    @Test fun customProfileTrimsAndLimitsName() {
        val avatar = AvatarContent.Character(ProfileCharacter.GAMJA)
        val state = GroupProfileState.from(member).copy(useDefault = false, avatar = avatar)
        assertFalse(state.copy(nickname = " a ").canSave)
        assertTrue(state.copy(nickname = " ab ").canSave)
        assertTrue(state.copy(nickname = "1234567890").canSave)
        assertEquals(10, state.withNickname("12345678901").nickname.length)
        assertEquals(Profile(avatar, "ab"), state.copy(nickname = " ab ").profileToSave)
        assertEquals(avatar, state.preview.avatar)
        assertEquals(member.defaultProfile, state.copy(useDefault = true).preview)
    }

    @Test fun existingOverrideStartsCustom() {
        val override = Profile(AvatarContent.Character(ProfileCharacter.GOMDOL), "곰돌")
        val state = GroupProfileState.from(member.copy(groupProfile = override))
        assertFalse(state.useDefault)
        assertEquals(override, state.preview)
    }
}
