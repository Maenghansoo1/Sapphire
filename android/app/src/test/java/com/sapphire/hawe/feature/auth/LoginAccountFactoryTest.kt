package com.sapphire.hawe.feature.auth

import com.sapphire.hawe.core.model.AvatarContent
import com.sapphire.hawe.core.model.LoginProvider
import com.sapphire.hawe.core.model.MyAccount
import com.sapphire.hawe.designsystem.assets.ProfileCharacter
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class LoginAccountFactoryTest {
    private val now = Instant.parse("2026-10-12T00:00:00Z")

    @Test fun unchangedEmailDoesNotCreateAccount() {
        val current = MyAccount.mock
        assertNull(accountForLogin("", current, now))
        assertNull(accountForLogin("  SKY@EMAIL.COM  ", current, now))
    }

    @Test fun newEmailCreatesDefaultAccount() {
        val account = accountForLogin("  LongNickname123@Example.COM  ", MyAccount.mock, now)!!
        assertEquals("longnickna", account.profile.nickname)
        assertEquals("longnickname123@example.com", account.email)
        assertEquals(AvatarContent.Character(ProfileCharacter.MONGSIL), account.profile.avatar)
        assertEquals(now, account.joinedAt)
        assertEquals(LoginProvider.EMAIL, account.signUpProvider)
        assertEquals(setOf(LoginProvider.EMAIL), account.linkedProviders)
    }

    @Test fun emptyPrefixUsesFallbackNickname() {
        assertEquals("하늘", accountForLogin("@example.com", MyAccount.mock, now)?.profile?.nickname)
    }
}
