package com.sapphire.hawe.feature.auth

import org.junit.Assert.*
import org.junit.Test

class SignupAccountStateTest {
    @Test fun emailValidation() {
        val state = SignupAccountState()
        assertFalse(state.isEmailValid)
        assertNull(state.emailError)
        state.email = "a@b.co"
        assertTrue(state.isEmailValid)
        state.email = "a@b"
        assertEquals("이메일 형식이 올바르지 않아요", state.emailError)
        state.email = "a b@c.com"
        assertFalse(state.isEmailValid)
        state.email = " a@b.co "
        assertFalse(state.isEmailValid)
    }

    @Test fun passwordAndConfirmation() {
        val state = SignupAccountState()
        assertNull(state.passwordError)
        assertNull(state.confirmError)
        for (password in listOf("abc1234", "abcdefgh", "12345678", "가나다라마1234")) {
            state.password = password
            assertFalse(password, state.isPasswordValid)
            assertEquals("8자 이상, 영문과 숫자를 함께 써 주세요", state.passwordError)
        }
        state.password = "abcd1234"
        assertTrue(state.isPasswordValid)
        state.passwordConfirm = "abcd1235"
        assertEquals("비밀번호가 일치하지 않아요", state.confirmError)
        state.passwordConfirm = "abcd1234"
        assertTrue(state.isConfirmValid)
    }

    @Test fun agreementAndProceed() {
        val state = SignupAccountState("a@b.co", "abcd1234", "abcd1234", availableEmail = "a@b.co")
        assertFalse(state.canProceed)
        state.agreeAll = true
        assertTrue(state.agreeService)
        assertTrue(state.agreePrivacy)
        assertTrue(state.canProceed)
        state.agreePrivacy = false
        assertFalse(state.agreeAll)
        assertFalse(state.canProceed)
        state.agreePrivacy = true
        state.agreeService = false
        assertFalse(state.agreeAll)
        assertFalse(state.canProceed)
        state.agreeAll = false
        assertFalse(state.agreeService)
    }

    @Test fun emailDuplicateCheck() {
        val state = SignupAccountState("A@b.co", "abcd1234", "abcd1234", agreeService = true, agreePrivacy = true)
        // 확인 전에는 다음으로 못 가요
        assertFalse(state.canProceed)
        assertTrue(state.canCheckEmail)
        assertEquals("중복 확인을 눌러 주세요", state.emailHelper)
        // 이미 가입된 이메일
        state.takenEmail = "a@b.co"
        assertEquals("이미 가입된 이메일이에요", state.emailError)
        assertFalse(state.canCheckEmail)
        assertFalse(state.canProceed)
        // 이메일을 고치면 다시 확인해요
        state.email = "b@b.co"
        assertNull(state.emailError)
        assertTrue(state.canCheckEmail)
        state.failedEmail = "b@b.co"
        assertEquals("확인하지 못했어요. 다시 눌러 주세요", state.emailError)
        assertTrue(state.canCheckEmail)
        state.availableEmail = "b@b.co"
        assertNull(state.emailError)
        assertEquals("사용할 수 있는 이메일이에요", state.emailHelper)
        assertFalse(state.canCheckEmail)
        assertTrue(state.canProceed)
        state.email = "c@b.co"
        assertFalse(state.canProceed)
    }
}
