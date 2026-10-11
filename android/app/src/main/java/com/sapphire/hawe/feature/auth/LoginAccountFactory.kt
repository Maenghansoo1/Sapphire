package com.sapphire.hawe.feature.auth

import com.sapphire.hawe.core.model.AvatarContent
import com.sapphire.hawe.core.model.LoginProvider
import com.sapphire.hawe.core.model.MyAccount
import com.sapphire.hawe.core.model.Profile
import com.sapphire.hawe.designsystem.assets.ProfileCharacter
import java.time.Instant
import java.util.Locale

fun accountForLogin(emailInput: String, current: MyAccount, now: Instant): MyAccount? {
    val email = emailInput.trim().lowercase(Locale.ROOT)
    if (email.isEmpty() || current.email?.equals(email, ignoreCase = true) == true) return null
    val nickname = email.substringBefore('@').take(10).ifEmpty { "하늘" }
    return MyAccount(Profile(AvatarContent.Character(ProfileCharacter.MONGSIL), nickname),
        email, now, LoginProvider.EMAIL, setOf(LoginProvider.EMAIL))
}
