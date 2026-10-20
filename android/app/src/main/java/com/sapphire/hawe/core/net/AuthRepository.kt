package com.sapphire.hawe.core.net

import android.net.Uri
import com.sapphire.hawe.core.model.AvatarContent
import com.sapphire.hawe.core.model.LoginProvider
import com.sapphire.hawe.core.model.MyAccount
import com.sapphire.hawe.core.model.Profile
import java.time.OffsetDateTime
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

data class SaveProfileResult(val account: MyAccount?, val error: Throwable?)

class AuthRepository(private val api: ApiClient, private val tokens: TokenStore) {
    val hasSession: Boolean get() = tokens.refreshToken != null

    suspend fun login(email: String, password: String): MyAccount {
        authenticate(email, password)
        return restore()
    }

    /** 가입 전에 이메일을 쓸 수 있는지(이미 가입된 이메일이면 false). */
    suspend fun isEmailAvailable(email: String): Boolean = api.checkEmail(EmailCheckRequestDto(email.trim())).available

    suspend fun signUp(email: String, password: String, nickname: String, avatar: AvatarContent,
        agreeTerms: Boolean, agreePrivacy: Boolean, skipRegister: Boolean, onRegistered: () -> Unit,
        readPhoto: (Uri) -> ByteArray): MyAccount {
        if (!skipRegister) {
            api.register(RegisterRequestDto(email.trim(), password, nickname.trim(), agreeTerms, agreePrivacy))
            onRegistered()
        }
        authenticate(email, password)
        try {
            when (avatar) {
                is AvatarContent.Character -> {
                    val page = api.characters()
                    page.content.firstOrNull { it.svgKey == avatar.character.value }?.let { api.updateCharacter(it.id) }
                }
                is AvatarContent.Photo -> api.uploadProfileImage(withContext(Dispatchers.IO) { readPhoto(avatar.uri) })
            }
        } catch (_: ApiException) {
            // The account is already registered and signed in; use the default character.
        } catch (_: NetworkException) {
            // A later profile edit can retry the character choice.
        } catch (_: IOException) {
            // A later profile edit can retry the photo choice.
        }
        return restore()
    }

    suspend fun restore(): MyAccount = api.me().toAccount()

    /** Retry temporary startup failures while retaining the stored refresh token. */
    suspend fun restoreWithRetry(delays: List<Long> = listOf(1000, 2000)): MyAccount {
        for (waitMs in delays) {
            try {
                return restore()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (e !is NetworkException && (e !is ApiException || e.status < 500)) throw e
                delay(waitMs)
            }
        }
        return restore()
    }

    suspend fun saveProfile(current: Profile, nickname: String, avatar: AvatarContent,
        readPhoto: (Uri) -> ByteArray): SaveProfileResult {
        var error: Throwable? = null
        try {
            if (nickname.trim() != current.nickname) api.updateNickname(nickname.trim())
            when (avatar) {
                is AvatarContent.Character -> {
                    val previous = current.avatar
                    if (previous !is AvatarContent.Character || previous.character != avatar.character) {
                        val character = api.characters().content.firstOrNull { it.svgKey == avatar.character.value }
                        if (character != null) api.updateCharacter(character.id)
                    }
                    if (previous.isPhoto) api.deleteProfileImage()
                }
                is AvatarContent.Photo -> if (avatar.uri.scheme == "file") {
                    api.uploadProfileImage(withContext(Dispatchers.IO) { readPhoto(avatar.uri) })
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = e
        }
        val account = try { restore() } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (error == null) error = e
            null
        }
        return SaveProfileResult(account, error)
    }

    suspend fun withdraw() = api.deleteMe()

    suspend fun changePassword(current: String, new: String) {
        val result = api.changePassword(current, new)
        tokens.save(result.accessToken, result.refreshToken)
    }

    fun clearSession() = tokens.clear()

    private suspend fun authenticate(email: String, password: String) {
        val result = api.login(LoginRequestDto(email.trim(), password))
        tokens.save(result.accessToken, result.refreshToken)
    }

    private fun MeDto.toAccount(): MyAccount {
        return MyAccount(Profile(api.avatarOf(characterKey, profileImageUrl), nickname), email,
            OffsetDateTime.parse(createdAt).toInstant(), LoginProvider.EMAIL, setOf(LoginProvider.EMAIL), id)
    }
}
