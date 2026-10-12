package com.sapphire.hawe.feature.groups

import com.sapphire.hawe.core.data.InviteLookupResult
import com.sapphire.hawe.core.model.GroupInvitePreview
import com.sapphire.hawe.core.model.InviteCode

data class JoinGroupState(
    val code: String = "", val preview: GroupInvitePreview? = null, val errorMessage: String? = null,
    val loading: Boolean = false,
) {
    fun withCode(input: String): JoinGroupState {
        val normalized = InviteCode.sanitize(input)
        if (normalized == code) return this
        return JoinGroupState(normalized, loading = normalized.length == InviteCode.LENGTH)
    }
    fun withResult(code: String, result: InviteLookupResult): JoinGroupState {
        if (this.code != code) return this
        return when (result) {
            is InviteLookupResult.Success -> copy(preview = result.preview, errorMessage = null, loading = false)
            is InviteLookupResult.Failure -> copy(preview = null, errorMessage = result.error.message, loading = false)
        }
    }
    fun withInitialCode(input: String): JoinGroupState = if (code.isEmpty()) withCode(input) else this
}
