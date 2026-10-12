package com.sapphire.hawe.core.net

fun Throwable.toUserMessage(): String = when (this) {
    is NetworkException -> "네트워크 연결을 확인해 주세요"
    is ApiException -> when (code) {
        "EMAIL_TAKEN" -> "이미 가입된 이메일이에요"
        "INVALID_CREDENTIALS" -> "이메일 또는 비밀번호가 올바르지 않아요"
        "VALIDATION_ERROR" -> "입력한 내용을 다시 확인해 주세요"
        "INVALID_TOKEN", "TOKEN_EXPIRED" -> "다시 로그인해 주세요"
        else -> "잠시 후 다시 시도해 주세요"
    }
    else -> "잠시 후 다시 시도해 주세요"
}
