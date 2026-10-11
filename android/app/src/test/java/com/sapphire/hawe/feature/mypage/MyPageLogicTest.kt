package com.sapphire.hawe.feature.mypage

import com.sapphire.hawe.core.model.*
import org.junit.Assert.*
import org.junit.Test

class MyPageLogicTest {
    @Test fun accountAndStats() {
        val base = MyAccount.mock
        assertEquals(listOf("sky@email.com"), accountLines(base.copy(signUpProvider = LoginProvider.EMAIL)))
        assertEquals(listOf("카카오 로그인"), accountLines(base.copy(email = null)))
        assertEquals(listOf("sky@email.com", "Apple 로그인"),
            accountLines(base.copy(signUpProvider = LoginProvider.APPLE)))
        assertEquals(emptyList<String>(), accountLines(base.copy(email = "", signUpProvider = LoginProvider.EMAIL)))
        assertEquals(StatsDisplay("0", "0일", "0개"), statsDisplay(ActivityStats(0, 0, 0, emptyMap())))
        assertEquals(StatsDisplay("12", "3일", "2개"), statsDisplay(ActivityStats(12, 3, 2, emptyMap())))
        assertEquals(0, byTypeCount(ActivityStats(0, 0, 0, emptyMap()), MissionType.PHOTO))
    }

    @Test fun notificationsAndProviders() {
        val prefs = NotificationPrefs()
        assertFalse(prefs.friendDone)
        val off = prefs.with(NotificationKey.ALL, false)
        assertFalse(individualEnabled(off))
        assertEquals(off, off.with(NotificationKey.TOPIC, false))
        assertTrue(off.with(NotificationKey.ALL, true).topic)
        val base = MyAccount.mock
        assertEquals(ProviderRowState.Linked(false), providerRowState(base, LoginProvider.KAKAO))
        assertEquals(ProviderRowState.NotLinked, providerRowState(base, LoginProvider.APPLE))
        assertEquals(base, base.unlinking(LoginProvider.KAKAO))
        val linked = base.linking(LoginProvider.APPLE)
        assertTrue(canUnlink(linked, LoginProvider.KAKAO))
        assertEquals(setOf(LoginProvider.APPLE), linked.unlinking(LoginProvider.KAKAO).linkedProviders)
        assertFalse(canUnlink(linked.unlinking(LoginProvider.KAKAO), LoginProvider.APPLE))
    }

    @Test fun passwordAndProfile() {
        assertTrue(ChangePasswordState(new = "abcd1234").isNewValid)
        listOf("abc1234", "abcdefgh", "12345678", "가나다라마1234").forEach {
            assertFalse(ChangePasswordState(new = it).isNewValid)
        }
        assertNull(ChangePasswordState().newError)
        assertNull(ChangePasswordState().confirmError)
        assertEquals("비밀번호가 일치하지 않아요", ChangePasswordState(new = "abcd1234", confirm = "x").confirmError)
        assertFalse(ChangePasswordState(new = "abcd1234", confirm = "abcd1234").canSave)
        assertFalse(ChangePasswordState("abcd1234", "abcd1234", "abcd1234").canSave)
        assertFalse(ChangePasswordState("old", "abcd1234", "abcd1235").canSave)
        assertTrue(ChangePasswordState("old", "abcd1234", "abcd1234").canSave)
        val avatar = Profile.mockMine.avatar
        assertFalse(ProfileEditState(avatar, "가").canSave)
        assertTrue(ProfileEditState(avatar, "가나").canSave)
        assertTrue(ProfileEditState(avatar, "1234567890").canSave)
        assertEquals("가나", ProfileEditState(avatar, " 가나 ").profileToSave.nickname)
        assertFalse(ProfileEditState(avatar, "   ").canSave)
    }

    @Test fun withdrawVersionAndUris() {
        assertFalse(WithdrawState().canWithdraw)
        assertTrue(WithdrawState(true).canWithdraw)
        assertTrue(AppVersionInfo.isLatest("1.0.0", "1.0.0"))
        assertEquals("새 버전이 있어요", AppVersionInfo.versionStatusTitle("1.0.0", "1.1.0"))
        assertEquals("mailto:a@b.com", mailtoUri("a@b.com"))
        assertEquals("market://details?id=com.sapphire.hawe", playStoreMarketUri("com.sapphire.hawe"))
        assertEquals("https://play.google.com/store/apps/details?id=com.sapphire.hawe",
            playStoreWebUri("com.sapphire.hawe"))
    }
}
