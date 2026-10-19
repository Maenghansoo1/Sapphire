package com.sapphire.hawe.auth.service;

import com.sapphire.hawe.auth.dao.UserMapper;
import com.sapphire.hawe.auth.domain.User;
import com.sapphire.hawe.auth.dto.EmailCheckRequest;
import com.sapphire.hawe.auth.dto.EmailCheckResponse;
import com.sapphire.hawe.auth.dto.LoginRequest;
import com.sapphire.hawe.auth.dto.MeResponse;
import com.sapphire.hawe.auth.dto.PasswordChangeRequest;
import com.sapphire.hawe.auth.dto.RefreshRequest;
import com.sapphire.hawe.auth.dto.RegisterRequest;
import com.sapphire.hawe.auth.dto.RegisterResponse;
import com.sapphire.hawe.auth.dto.TokenResponse;
import com.sapphire.hawe.character.dao.CharacterMapper;
import com.sapphire.hawe.character.domain.ProfileCharacter;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.security.JwtTokenProvider;
import com.sapphire.hawe.global.security.TokenErrorCode;
import com.sapphire.hawe.global.security.TokenClaims;
import com.sapphire.hawe.global.storage.FileStorage;
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
    private final FileStorage storage;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider,
            CharacterMapper characterMapper, FileStorage storage) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.characterMapper = characterMapper;
        this.storage = storage;
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

    /** 가입 전에 이메일을 쓸 수 있는지 알려 준다. 그사이 다른 사람이 가입할 수 있어서 register 도 다시 확인한다. */
    public EmailCheckResponse checkEmail(EmailCheckRequest request) {
        return new EmailCheckResponse(!userMapper.existsByEmail(request.email()));
    }

    public TokenResponse login(LoginRequest request) {
        User user = userMapper.findByEmail(request.email());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        return issueTokens(user);
    }

    public TokenResponse refresh(RefreshRequest request) {
        TokenClaims claims = jwtTokenProvider.parseRefreshToken(request.refreshToken());
        User user = userMapper.findById(claims.userId());
        if (user == null || user.getTokenVersion() != claims.tokenVersion()) {
            throw new BusinessException(TokenErrorCode.INVALID_TOKEN);
        }
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse changePassword(Long userId, PasswordChangeRequest request) {
        User user = userMapper.findById(userId);
        if (user == null) throw new BusinessException(TokenErrorCode.INVALID_TOKEN);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash()))
            throw new BusinessException(AuthErrorCode.WRONG_CURRENT_PASSWORD);
        String hash = passwordEncoder.encode(request.newPassword());
        userMapper.changePassword(userId, hash);
        User updated = userMapper.findById(userId);
        if (updated == null) throw new BusinessException(TokenErrorCode.INVALID_TOKEN);
        return issueTokens(updated);
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
        return new MeResponse(user.getId(), user.getEmail(), user.getNickname(), characterKey,
                user.getProfileImageUrl() == null ? null : storage.publicUrl(user.getProfileImageUrl()),
                SeoulTime.toOffset(user.getCreatedAt()));
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getNickname(), user.getTokenVersion());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getTokenVersion());
        return new TokenResponse(accessToken, refreshToken);
    }
}
