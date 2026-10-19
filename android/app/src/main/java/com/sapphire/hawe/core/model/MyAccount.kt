package com.sapphire.hawe.core.model

import com.sapphire.hawe.core.HaweTime
import java.time.Instant
import java.time.LocalDate

enum class LoginProvider(val value: String, val displayName: String) {
    EMAIL("email", "이메일"), KAKAO("kakao", "카카오"), APPLE("apple", "Apple"), GOOGLE("google", "Google")
}

data class NotificationPrefs(
    val all: Boolean = true, val topic: Boolean = true, val poke: Boolean = true,
    val comment: Boolean = true,
)

data class ActivityStats(
    val completedMissions: Int, val streakDays: Int, val groupCount: Int,
    val byType: Map<MissionType, Int>,
)

data class MyAccount(
    val profile: Profile, val email: String?, val joinedAt: Instant,
    val signUpProvider: LoginProvider, val linkedProviders: Set<LoginProvider>, val userId: Long = 0L,
) {
    val canChangePassword: Boolean get() = LoginProvider.EMAIL in linkedProviders
    companion object {
        val mock get() = MyAccount(Profile.mockMine, "sky@email.com",
            LocalDate.of(2026, 6, 2).atStartOfDay(HaweTime.SEOUL).toInstant(),
            LoginProvider.KAKAO, setOf(LoginProvider.KAKAO))
    }
}
