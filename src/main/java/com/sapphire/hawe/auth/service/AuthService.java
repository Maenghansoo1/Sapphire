package com.sapphire.hawe.auth.service;

import com.sapphire.hawe.auth.dao.UserMapper;
import com.sapphire.hawe.auth.domain.User;
import com.sapphire.hawe.auth.dto.LoginRequest;
import com.sapphire.hawe.auth.dto.RefreshRequest;
import com.sapphire.hawe.auth.dto.RegisterRequest;
import com.sapphire.hawe.auth.dto.RegisterResponse;
import com.sapphire.hawe.auth.dto.TokenResponse;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.security.JwtTokenProvider;
import com.sapphire.hawe.global.security.TokenErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userMapper.existsByLoginId(request.loginId())) {
            throw new BusinessException(AuthErrorCode.LOGIN_ID_TAKEN);
        }

        User user = new User();
        user.setLoginId(request.loginId());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setEmail(request.email());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(AuthErrorCode.LOGIN_ID_TAKEN);
        }

        return new RegisterResponse(user.getId(), user.getLoginId(), user.getNickname());
    }

    public TokenResponse login(LoginRequest request) {
        User user = userMapper.findByLoginId(request.loginId());
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

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getNickname());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        return new TokenResponse(accessToken, refreshToken);
    }
}
