package com.sapphire.hawe.core.net

import kotlinx.serialization.Serializable

@Serializable internal data class PokeRequestDto(val toUserId: Long, val type: String)
@Serializable internal data class PokeResponseDto(val id: Long, val toUserId: Long, val type: String,
    val missionId: Long, val sentAt: String? = null)
