package com.sapphire.hawe.global.security;

import com.sapphire.hawe.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenProviderTests {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void expiredAccessTokenReturnsTokenExpired() {
        JwtTokenProvider provider = provider(-1);
        String token = provider.createAccessToken(42L, "tester");

        BusinessException error = assertThrows(BusinessException.class,
                () -> provider.validateAndGetUserId(token));

        assertEquals(TokenErrorCode.TOKEN_EXPIRED, error.errorCode());
    }

    @Test
    void validAccessTokenReturnsUserId() {
        JwtTokenProvider provider = provider(1800);
        String token = provider.createAccessToken(42L, "tester");

        assertEquals(42L, provider.validateAndGetUserId(token));
    }

    @Test
    void refreshTokenCannotAuthenticateAsAccessToken() {
        JwtTokenProvider provider = provider(1800);
        String token = provider.createRefreshToken(42L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> provider.validateAndGetUserId(token));

        assertEquals(TokenErrorCode.INVALID_TOKEN, error.errorCode());
    }

    private static JwtTokenProvider provider(long accessSeconds) {
        return new JwtTokenProvider(new JwtProperties(SECRET, accessSeconds, 1209600));
    }
}
