package com.sapphire.hawe.core.model

val AvatarContent.choiceName: String
    get() = when (this) {
        is AvatarContent.Character -> character.displayName
        is AvatarContent.Photo -> "내 사진"
    }
