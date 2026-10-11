package com.sapphire.hawe.feature.groups

import com.sapphire.hawe.core.data.InviteLookupResult
import com.sapphire.hawe.core.model.GroupInvitePreview
import com.sapphire.hawe.core.model.InviteCode

data class JoinGroupState(
    val code: String = "", val preview: GroupInvitePreview? = null, val errorMessage: String? = null,
) {
    fun withCode(input: String, lookUp: (String) -> InviteLookupResult): JoinGroupState {
        val normalized = InviteCode.sanitize(input)
        if (normalized.length != InviteCode.LENGTH) return JoinGroupState(normalized)
        return when (val result = lookUp(normalized)) {
            is InviteLookupResult.Success -> JoinGroupState(normalized, preview = result.preview)
            is InviteLookupResult.Failure -> JoinGroupState(normalized, errorMessage = result.error.message)
        }
    }
    fun withInitialCode(input: String, lookUp: (String) -> InviteLookupResult): JoinGroupState =
        if (code.isEmpty()) withCode(input, lookUp) else this
}
