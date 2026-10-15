package com.sapphire.hawe.core.net

import android.net.Uri
import com.sapphire.hawe.core.model.AvatarContent
import com.sapphire.hawe.designsystem.assets.ProfileCharacter

internal fun ApiClient.avatarOf(characterKey: String?, profileImageUrl: String?): AvatarContent =
    profileImageUrl?.takeIf { it.isNotBlank() }?.let {
        AvatarContent.Photo(Uri.parse(absoluteUrl(it)))
    } ?: AvatarContent.Character(ProfileCharacter.fromKey(characterKey))
