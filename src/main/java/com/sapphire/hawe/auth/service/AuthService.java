package com.sapphire.hawe.auth.service;

import com.sapphire.hawe.auth.dao.UserMapper;
import com.sapphire.hawe.auth.domain.User;
import com.sapphire.hawe.auth.dto.LoginRequest;
import com.sapphire.hawe.auth.dto.MeResponse;
import com.sapphire.hawe.auth.dto.RefreshRequest;
import com.sapphire.hawe.auth.dto.RegisterRequest;
import com.sapphire.hawe.auth.dto.RegisterResponse;
import com.sapphire.hawe.auth.dto.TokenResponse;
import com.sapphire.hawe.character.dao.CharacterMapper;
import com.sapphire.hawe.character.domain.ProfileCharacter;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.security.JwtTokenProvider;
import com.sapphire.hawe.global.security.TokenErrorCode;
import com.sapphire.hawe.global.time.SeoulTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final CharacterMapper characterMapper;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider,
            CharacterMapper characterMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.characterMapper = characterMapper;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userMapper.existsByEmail(request.email())) {
            throw new BusinessException(AuthErrorCode.EMAIL_TAKEN);
        }

        User user = new User();
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setEmail(request.email());
        LocalDateTime agreedAt = SeoulTime.now();
        user.setTermsAgreedAt(agreedAt);
        user.setPrivacyAgreedAt(agreedAt);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(AuthErrorCode.EMAIL_TAKEN);
        }

        return new RegisterResponse(user.getId(), user.getEmail(), user.getNickname());
    }

    public TokenResponse login(LoginRequest request) {
        User user = userMapper.findByEmail(request.email());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        return issueTokens(user);
    }

    public TokenResponse refresh(RefreshRequest request) {
        Long userId = jwtTokenProvider.validateRefreshTokenAndGetUserId(request.refreshToken());
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(TokenErrorCode.INVALID_TOKEN);
        }
        return issueTokens(user);
    }

    public MeResponse getMe(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(TokenErrorCode.INVALID_TOKEN);
        }
        String characterKey = null;
        if (user.getCharacterId() != null) {
            ProfileCharacter character = characterMapper.findById(user.getCharacterId());
            if (character != null) characterKey = character.getSvgKey();
        }
        return new MeResponse(user.getId(), user.getEmail(), user.getNickname(), characterKey, null,
                SeoulTime.toOffset(user.getCreatedAt()));
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getNickname());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        return new TokenResponse(accessToken, refreshToken);
    }
}
