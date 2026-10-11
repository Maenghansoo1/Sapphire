package com.sapphire.hawe.core.model

object InviteCode {
    const val LENGTH = 6
    fun sanitize(text: String): String = text.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(LENGTH)
}
