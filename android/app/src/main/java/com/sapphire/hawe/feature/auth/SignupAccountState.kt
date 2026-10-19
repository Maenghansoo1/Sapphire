package com.sapphire.hawe.feature.auth

class SignupAccountState(
    var email: String = "", var password: String = "", var passwordConfirm: String = "",
    var agreeService: Boolean = false, var agreePrivacy: Boolean = false,
    /** 중복 확인 결과. 서버처럼 앞뒤 공백을 빼고 소문자로 맞춘 이메일로 기억해요. */
    var availableEmail: String? = null, var takenEmail: String? = null, var failedEmail: String? = null,
) {
    var agreeAll: Boolean
        get() = agreeService && agreePrivacy
        set(value) { agreeService = value; agreePrivacy = value }

    val isEmailValid: Boolean get() = EMAIL_PATTERN.matches(email)
    val isPasswordValid: Boolean get() = password.length >= 8 &&
        password.any { it in 'A'..'Z' || it in 'a'..'z' } && password.any { it in '0'..'9' }
    val isConfirmValid: Boolean get() = passwordConfirm.isNotEmpty() && passwordConfirm == password
    val normalizedEmail: String get() = email.trim().lowercase()
    /** 지금 적힌 이메일로 중복 확인을 통과했는지. 이메일을 고치면 다시 확인해야 해요. */
    val isEmailChecked: Boolean get() = isEmailValid && availableEmail == normalizedEmail
    val canCheckEmail: Boolean get() = isEmailValid && !isEmailChecked && takenEmail != normalizedEmail
    val emailError: String? get() = when {
        email.isEmpty() || isEmailChecked -> null
        !isEmailValid -> "이메일 형식이 올바르지 않아요"
        takenEmail == normalizedEmail -> "이미 가입된 이메일이에요"
        failedEmail == normalizedEmail -> "확인하지 못했어요. 다시 눌러 주세요"
        else -> null
    }
    val emailHelper: String? get() = when {
        isEmailChecked -> "사용할 수 있는 이메일이에요"
        isEmailValid && emailError == null -> "중복 확인을 눌러 주세요"
        else -> null
    }
    val passwordError: String? get() = if (password.isEmpty() || isPasswordValid) null else "8자 이상, 영문과 숫자를 함께 써 주세요"
    val confirmError: String? get() = if (passwordConfirm.isEmpty() || passwordConfirm == password) null else "비밀번호가 일치하지 않아요"
    val canProceed: Boolean get() = isEmailChecked && isPasswordValid && isConfirmValid && agreeAll

    companion object { private val EMAIL_PATTERN = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") }
}
