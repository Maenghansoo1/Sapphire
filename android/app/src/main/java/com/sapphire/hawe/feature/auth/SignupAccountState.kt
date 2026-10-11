package com.sapphire.hawe.feature.auth

class SignupAccountState(
    var email: String = "", var password: String = "", var passwordConfirm: String = "",
    var agreeService: Boolean = false, var agreePrivacy: Boolean = false,
) {
    var agreeAll: Boolean
        get() = agreeService && agreePrivacy
        set(value) { agreeService = value; agreePrivacy = value }

    val isEmailValid: Boolean get() = EMAIL_PATTERN.matches(email)
    val isPasswordValid: Boolean get() = password.length >= 8 &&
        password.any { it in 'A'..'Z' || it in 'a'..'z' } && password.any { it in '0'..'9' }
    val isConfirmValid: Boolean get() = passwordConfirm.isNotEmpty() && passwordConfirm == password
    val emailError: String? get() = if (email.isEmpty() || isEmailValid) null else "이메일 형식이 올바르지 않아요"
    val passwordError: String? get() = if (password.isEmpty() || isPasswordValid) null else "8자 이상, 영문과 숫자를 함께 써 주세요"
    val confirmError: String? get() = if (passwordConfirm.isEmpty() || passwordConfirm == password) null else "비밀번호가 일치하지 않아요"
    val canProceed: Boolean get() = isEmailValid && isPasswordValid && isConfirmValid && agreeAll

    companion object { private val EMAIL_PATTERN = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") }
}
