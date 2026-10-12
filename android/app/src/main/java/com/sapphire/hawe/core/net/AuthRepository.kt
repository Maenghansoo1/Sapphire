package com.sapphire.hawe.core.net

import com.sapphire.hawe.core.model.AvatarContent
import com.sapphire.hawe.core.model.LoginProvider
import com.sapphire.hawe.core.model.MyAccount
import com.sapphire.hawe.core.model.Profile
import com.sapphire.hawe.designsystem.assets.ProfileCharacter
import java.time.OffsetDateTime

class AuthRepository(private val api: ApiClient, private val tokens: TokenStore) {
    val hasSession: Boolean get() = tokens.refreshToken != null

    suspend fun login(email: String, password: String): MyAccount {
        authenticate(email, password)
        return restore()
    }

    suspend fun signUp(email: String, password: String, nickname: String, character: ProfileCharacter,
        agreeTerms: Boolean, agreePrivacy: Boolean, skipRegister: Boolean, onRegistered: () -> Unit): MyAccount {
        if (!skipRegister) {
            api.register(RegisterRequestDto(email.trim(), password, nickname.trim(), agreeTerms, agreePrivacy))
            onRegistered()
        }
        authenticate(email, password)
        try {
            val page = api.characters()
            page.content.firstOrNull { it.svgKey == character.value }?.let {
                api.updateCharacter(it.id)
            }
        } catch (_: ApiException) {
            // The account is already registered and signed in; use the default character.
        } catch (_: NetworkException) {
            // A later profile edit can retry the character choice.
        }
        return restore()
    }

    suspend fun restore(): MyAccount = api.me().toAccount()

    fun clearSession() = tokens.clear()

    private suspend fun authenticate(email: String, password: String) {
        val result = api.login(LoginRequestDto(email.trim(), password))
        tokens.save(result.accessToken, result.refreshToken)
    }

    private fun MeDto.toAccount(): MyAccount {
        val character = ProfileCharacter.entries.firstOrNull { it.value == characterKey } ?: ProfileCharacter.DEFAULT
        return MyAccount(Profile(AvatarContent.Character(character), nickname), email,
            OffsetDateTime.parse(createdAt).toInstant(), LoginProvider.EMAIL, setOf(LoginProvider.EMAIL))
    }
}
