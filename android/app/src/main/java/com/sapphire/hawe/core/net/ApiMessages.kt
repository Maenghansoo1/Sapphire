package com.sapphire.hawe.core.net

fun Throwable.toUserMessage(): String = when (this) {
    is NetworkException -> "네트워크 연결을 확인해 주세요"
    is ApiException -> when (code) {
        "EMAIL_TAKEN" -> "이미 가입된 이메일이에요"
        "INVALID_CREDENTIALS" -> "이메일 또는 비밀번호가 올바르지 않아요"
        "VALIDATION_ERROR" -> "입력한 내용을 다시 확인해 주세요"
        "INVITE_NOT_FOUND" -> "초대 코드를 찾을 수 없어요"
        "GROUP_FULL" -> "이 그룹은 정원이 다 찼어요"
        "ALREADY_MEMBER" -> "이미 참여 중인 그룹이에요"
        "GROUP_NOT_FOUND" -> "그룹을 찾을 수 없어요"
        "NOT_GROUP_MEMBER" -> "이 그룹의 멤버가 아니에요"
        "SUCCESSOR_REQUIRED" -> "그룹장은 다른 멤버에게 그룹장을 넘긴 뒤 나갈 수 있어요. 지금은 준비 중이에요"
        "INVALID_TOKEN", "TOKEN_EXPIRED" -> "다시 로그인해 주세요"
        else -> "잠시 후 다시 시도해 주세요"
    }
    else -> "잠시 후 다시 시도해 주세요"
}
