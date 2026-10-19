package com.sapphire.hawe.core.net

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiMessagesTest {
    @Test fun mapsKnownErrorsWithoutExposingServerMessage() {
        assertEquals("이미 가입된 이메일이에요", ApiException(409, "EMAIL_TAKEN", "secret").toUserMessage())
        assertEquals("이메일 또는 비밀번호가 올바르지 않아요", ApiException(401, "INVALID_CREDENTIALS", "secret").toUserMessage())
        assertEquals("입력한 내용을 다시 확인해 주세요", ApiException(400, "VALIDATION_ERROR", "secret").toUserMessage())
        assertEquals("네트워크 연결을 확인해 주세요", NetworkException(IOException()).toUserMessage())
        assertEquals("다른 멤버가 있는 그룹의 그룹장이에요. 먼저 그룹장을 넘기고 탈퇴해 주세요",
            ApiException(409, "OWNER_MUST_TRANSFER", "secret").toUserMessage())
        assertEquals("사진 용량이 너무 커요 (10MB 이하)",
            ApiException(413, "FILE_TOO_LARGE", "secret").toUserMessage())
        assertEquals("지원하지 않는 사진 형식이에요",
            ApiException(415, "UNSUPPORTED_FILE_TYPE", "secret").toUserMessage())
        assertEquals("그룹장을 받을 멤버를 다시 골라 주세요",
            ApiException(409, "SUCCESSOR_REQUIRED", "secret").toUserMessage())
        assertEquals("그룹장을 받을 멤버를 다시 골라 주세요",
            ApiException(400, "INVALID_SUCCESSOR", "secret").toUserMessage())
        assertEquals("그룹장만 할 수 있어요", ApiException(403, "NOT_GROUP_OWNER", "secret").toUserMessage())
        assertEquals("현재 비밀번호가 맞지 않아요",
            ApiException(400, "WRONG_CURRENT_PASSWORD", "secret").toUserMessage())
        assertEquals("시도가 너무 많아요. 잠시 후 다시 시도해 주세요",
            ApiException(429, "TOO_MANY_REQUESTS", "secret").toUserMessage())
    }
}
