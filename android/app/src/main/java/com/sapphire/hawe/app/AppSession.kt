package com.sapphire.hawe.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sapphire.hawe.model.LoginProvider
import com.sapphire.hawe.model.Profile
import java.time.LocalDate

/** 내 계정 — 기본 프로필, 로그인 연결 (iOS `MyAccount` 와 같은 구조) */
data class MyAccount(
    val profile: Profile,
    val email: String?,
    val joinedAt: LocalDate,
    /** 처음 가입한 방법 */
    val signUpProvider: LoginProvider,
    val linkedProviders: Set<LoginProvider>,
) {
    /** 비밀번호 변경은 이메일 가입자만 (CLAUDE.md §4 17-2) */
    val canChangePassword: Boolean get() = LoginProvider.Email in linkedProviders

    companion object {
        /** Figma 17 · 17-2 화면 값 */
        val Mock = MyAccount(Profile.MockMine, "sky@email.com", LocalDate.of(2026, 6, 2), LoginProvider.Kakao, setOf(LoginProvider.Kakao))
    }
}

/**
 * 앱 전체 로그인 상태와 내 계정. 서버 연결 전이라 버튼을 누르면 바로 상태만 바꿔요.
 * TODO: Spring Boot 서버 연결 시 실제 로그인·토큰 처리
 */
class AppSession {
    var isSignedIn by mutableStateOf(false)
        private set
    var account by mutableStateOf(MyAccount.Mock)

    fun signIn() {
        isSignedIn = true
    }

    fun signOut() {
        isSignedIn = false
    }
}
