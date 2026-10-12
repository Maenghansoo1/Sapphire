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
    }
}
