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
        "SUCCESSOR_REQUIRED", "INVALID_SUCCESSOR" -> "그룹장을 받을 멤버를 다시 골라 주세요"
        "NOT_GROUP_OWNER" -> "그룹장만 할 수 있어요"
        "TOO_MANY_REQUESTS" -> "시도가 너무 많아요. 잠시 후 다시 시도해 주세요"
        "WRONG_CURRENT_PASSWORD" -> "현재 비밀번호가 맞지 않아요"
        "TARGET_NOT_GROUP_MEMBER" -> "그룹을 나간 멤버예요"
        "CANNOT_POKE_SELF" -> "나에게는 보낼 수 없어요"
        "INVALID_TOKEN", "TOKEN_EXPIRED" -> "다시 로그인해 주세요"
        "OWNER_MUST_TRANSFER" -> "다른 멤버가 있는 그룹의 그룹장이에요. 먼저 그룹장을 넘기고 탈퇴해 주세요"
        "FILE_TOO_LARGE" -> "사진 용량이 너무 커요 (10MB 이하)"
        "UNSUPPORTED_FILE_TYPE" -> "지원하지 않는 사진 형식이에요"
        else -> "잠시 후 다시 시도해 주세요"
    }
    else -> "잠시 후 다시 시도해 주세요"
}
