package com.sapphire.hawe.core.net

import kotlinx.serialization.Serializable

@Serializable data class RegisterRequestDto(val email: String, val password: String, val nickname: String,
    val agreeTerms: Boolean, val agreePrivacy: Boolean)
@Serializable data class RegisterResponseDto(val id: Long, val email: String, val nickname: String)
@Serializable internal data class EmailCheckRequestDto(val email: String)
@Serializable data class EmailCheckResponseDto(val available: Boolean)
@Serializable data class LoginRequestDto(val email: String, val password: String)
@Serializable data class RefreshRequestDto(val refreshToken: String)
@Serializable data class TokenResponseDto(val accessToken: String, val refreshToken: String)
@Serializable data class MeDto(val id: Long, val email: String, val nickname: String,
    val characterKey: String? = null, val profileImageUrl: String? = null, val createdAt: String)
@Serializable data class CharacterDto(val id: Long, val name: String, val svgKey: String)
@Serializable data class PageDto<T>(val content: List<T>)
@Serializable data class CharacterUpdateDto(val characterId: Long)
@Serializable data class CharacterUpdateResponseDto(val characterId: Long, val name: String, val svgKey: String)
@Serializable internal data class ProfileUpdateDto(val nickname: String)
@Serializable internal data class ProfileImageResponseDto(val profileImageUrl: String)
@Serializable internal data class PasswordChangeRequestDto(val currentPassword: String, val newPassword: String)
@Serializable data class ErrorBody(val code: String, val message: String)

internal suspend fun ApiClient.register(request: RegisterRequestDto): RegisterResponseDto =
    post("/api/auth/register", request, auth = false)
internal suspend fun ApiClient.checkEmail(request: EmailCheckRequestDto): EmailCheckResponseDto =
    post("/api/auth/email-check", request, auth = false)
internal suspend fun ApiClient.login(request: LoginRequestDto): TokenResponseDto =
    post("/api/auth/login", request, auth = false)
internal suspend fun ApiClient.me(): MeDto = get("/api/me")
internal suspend fun ApiClient.characters(): PageDto<CharacterDto> = get("/api/characters?page=0&size=100", auth = false)
internal suspend fun ApiClient.updateCharacter(id: Long): CharacterUpdateResponseDto =
    patch("/api/me/character", CharacterUpdateDto(id))
internal suspend fun ApiClient.updateNickname(nickname: String): MeDto =
    patch("/api/me/profile", ProfileUpdateDto(nickname))
internal suspend fun ApiClient.uploadProfileImage(bytes: ByteArray): ProfileImageResponseDto =
    putMultipart("/api/me/profile-image", listOf(
        ApiClient.MultipartPart.Bytes("file", "profile.jpg", "image/jpeg", bytes)))
internal suspend fun ApiClient.deleteProfileImage() = delete("/api/me/profile-image")
internal suspend fun ApiClient.deleteMe() = delete("/api/me")
internal suspend fun ApiClient.changePassword(current: String, new: String): TokenResponseDto =
    patch("/api/me/password", PasswordChangeRequestDto(current, new))
