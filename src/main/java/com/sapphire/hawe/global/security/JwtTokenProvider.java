package com.sapphire.hawe.global.security;

import com.sapphire.hawe.global.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Component
@EnableConfigurationProperties(JwtProperties.class)
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_NICKNAME = "nickname";
    private static final String CLAIM_TOKEN_VERSION = "tv";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId, String nickname) {
        return createAccessToken(userId, nickname, 0);
    }

    public String createAccessToken(Long userId, String nickname, int tokenVersion) {
        return createToken(userId, Map.of(CLAIM_TYPE, TYPE_ACCESS, CLAIM_NICKNAME, nickname,
                        CLAIM_TOKEN_VERSION, tokenVersion),
                properties.accessTokenValiditySeconds());
    }

    public String createRefreshToken(Long userId) {
        return createRefreshToken(userId, 0);
    }

    public String createRefreshToken(Long userId, int tokenVersion) {
        return createToken(userId, Map.of(CLAIM_TYPE, TYPE_REFRESH, CLAIM_TOKEN_VERSION, tokenVersion),
                properties.refreshTokenValiditySeconds());
    }

    /** Validates the token is well-formed, unexpired, and actually a refresh token. */
    public Long validateRefreshTokenAndGetUserId(String token) {
        return parseRefreshToken(token).userId();
    }

    public TokenClaims parseRefreshToken(String token) {
        Claims claims = parseClaims(token);
        if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE))) {
            throw new BusinessException(TokenErrorCode.INVALID_TOKEN);
        }
        return new TokenClaims(parseId(claims), parseTokenVersion(claims));
    }

    /** Used by the request-authentication filter; only access tokens may authenticate requests. */
    public Long validateAndGetUserId(String token) {
        return parseAccessToken(token).userId();
    }

    public TokenClaims parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE))) {
            throw new BusinessException(TokenErrorCode.INVALID_TOKEN);
        }
        return new TokenClaims(parseId(claims), parseTokenVersion(claims));
    }

    private int parseTokenVersion(Claims claims) {
        Object value = claims.get(CLAIM_TOKEN_VERSION);
        if (value == null) return 0;
        if (value instanceof Number number) return number.intValue();
        throw new BusinessException(TokenErrorCode.INVALID_TOKEN);
    }

    private Long parseId(Claims claims) {
        try {
            long id = Long.parseLong(claims.getSubject());
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException e) {
            // A signed token with an invalid subject is still an invalid token.
        }
        throw new BusinessException(TokenErrorCode.INVALID_TOKEN);
    }

    private String createToken(Long userId, Map<String, Object> claims, long validitySeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(validitySeconds)))
                .signWith(key)
                .compact();
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(TokenErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(TokenErrorCode.INVALID_TOKEN);
        }
    }
}
