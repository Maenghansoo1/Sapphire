package com.sapphire.hawe.global.security;

import com.sapphire.hawe.global.exception.BusinessException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
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

    @Test
    void parsesVersionedAndLegacyTokens() {
        JwtTokenProvider provider = provider(1800);
        String access = provider.createAccessToken(42L, "tester", 7);
        String refresh = provider.createRefreshToken(42L, 7);
        assertEquals(new TokenClaims(42L, 7), provider.parseAccessToken(access));
        assertEquals(new TokenClaims(42L, 7), provider.parseRefreshToken(refresh));
        assertEquals(42L, provider.validateAndGetUserId(access));
        assertEquals(42L, provider.validateRefreshTokenAndGetUserId(refresh));
        assertEquals(0, provider.parseAccessToken(provider.createAccessToken(42L, "tester")).tokenVersion());
        assertEquals(0, provider.parseRefreshToken(provider.createRefreshToken(42L)).tokenVersion());
        assertEquals(0, provider.parseAccessToken(signed("access", null)).tokenVersion());
    }

    @Test
    void rejectsWrongTokenTypeAndNonNumericVersion() {
        JwtTokenProvider provider = provider(1800);
        assertEquals(TokenErrorCode.INVALID_TOKEN,
                assertThrows(BusinessException.class,
                        () -> provider.parseRefreshToken(provider.createAccessToken(42L, "tester", 1))).errorCode());
        assertEquals(TokenErrorCode.INVALID_TOKEN,
                assertThrows(BusinessException.class,
                        () -> provider.parseAccessToken(provider.createRefreshToken(42L, 1))).errorCode());
        assertEquals(TokenErrorCode.INVALID_TOKEN,
                assertThrows(BusinessException.class,
                        () -> provider.parseAccessToken(signed("access", "invalid"))).errorCode());
    }

    private static String signed(String type, Object version) {
        var builder = Jwts.builder().subject("42").claim("type", type)
                .issuedAt(Date.from(Instant.now())).expiration(Date.from(Instant.now().plusSeconds(1800)));
        if (version != null) builder.claim("tv", version);
        return builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private static JwtTokenProvider provider(long accessSeconds) {
        return new JwtTokenProvider(new JwtProperties(SECRET, accessSeconds, 1209600));
    }
}
