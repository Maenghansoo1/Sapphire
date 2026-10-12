package com.sapphire.hawe.core.net

import kotlinx.serialization.Serializable

@Serializable data class RegisterRequestDto(val email: String, val password: String, val nickname: String,
    val agreeTerms: Boolean, val agreePrivacy: Boolean)
@Serializable data class RegisterResponseDto(val id: Long, val email: String, val nickname: String)
@Serializable data class LoginRequestDto(val email: String, val password: String)
@Serializable data class RefreshRequestDto(val refreshToken: String)
@Serializable data class TokenResponseDto(val accessToken: String, val refreshToken: String)
@Serializable data class MeDto(val id: Long, val email: String, val nickname: String,
    val characterKey: String? = null, val profileImageUrl: String? = null, val createdAt: String)
@Serializable data class CharacterDto(val id: Long, val name: String, val svgKey: String)
@Serializable data class PageDto<T>(val content: List<T>)
@Serializable data class CharacterUpdateDto(val characterId: Long)
@Serializable data class CharacterUpdateResponseDto(val characterId: Long, val name: String, val svgKey: String)
@Serializable data class ErrorBody(val code: String, val message: String)

internal suspend fun ApiClient.register(request: RegisterRequestDto): RegisterResponseDto =
    post("/api/auth/register", request, auth = false)
internal suspend fun ApiClient.login(request: LoginRequestDto): TokenResponseDto =
    post("/api/auth/login", request, auth = false)
internal suspend fun ApiClient.me(): MeDto = get("/api/me")
internal suspend fun ApiClient.characters(): PageDto<CharacterDto> = get("/api/characters?page=0&size=100", auth = false)
internal suspend fun ApiClient.updateCharacter(id: Long): CharacterUpdateResponseDto =
    patch("/api/me/character", CharacterUpdateDto(id))
