package com.sapphire.hawe.auth.service;

import com.sapphire.hawe.auth.dao.UserMapper;
import com.sapphire.hawe.auth.domain.User;
import com.sapphire.hawe.auth.dto.LoginRequest;
import com.sapphire.hawe.auth.dto.PasswordChangeRequest;
import com.sapphire.hawe.auth.dto.RefreshRequest;
import com.sapphire.hawe.auth.dto.RegisterRequest;
import com.sapphire.hawe.character.dao.CharacterMapper;
import com.sapphire.hawe.character.domain.ProfileCharacter;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.security.JwtTokenProvider;
import com.sapphire.hawe.global.security.TokenErrorCode;
import com.sapphire.hawe.global.security.TokenClaims;
import com.sapphire.hawe.global.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    @Mock UserMapper users;
    @Mock JwtTokenProvider tokens;
    @Mock CharacterMapper characters;
    @Mock FileStorage storage;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, encoder, tokens, characters, storage);
    }

    @Test
    void registerNormalizesAndStoresConsentWithHashedPassword() {
        doAnswer(call -> { ((User) call.getArgument(0)).setId(7L); return null; }).when(users).insert(any());
        var response = service.register(new RegisterRequest(" Sky@Email.com ", "abcd1234", " 하늘 ", true, true));
        verify(users).existsByEmail("sky@email.com");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).insert(saved.capture());
        assertEquals("sky@email.com", saved.getValue().getEmail());
        assertEquals("하늘", saved.getValue().getNickname());
        assertFalse("abcd1234".equals(saved.getValue().getPasswordHash()));
        assertEquals(true, encoder.matches("abcd1234", saved.getValue().getPasswordHash()));
        assertNotNull(saved.getValue().getTermsAgreedAt());
        assertNotNull(saved.getValue().getPrivacyAgreedAt());
        assertEquals(saved.getValue().getTermsAgreedAt(), saved.getValue().getPrivacyAgreedAt());
        assertEquals("sky@email.com", response.email());
    }

    @Test
    void duplicateEmailAndInsertRaceReturnEmailTaken() {
        var request = new RegisterRequest("a@test.com", "abcd1234", "하늘", true, true);
        when(users.existsByEmail("a@test.com")).thenReturn(true);
        assertEquals(AuthErrorCode.EMAIL_TAKEN,
                assertThrows(BusinessException.class, () -> service.register(request)).errorCode());
        when(users.existsByEmail("a@test.com")).thenReturn(false);
        doThrow(new DuplicateKeyException("duplicate")).when(users).insert(any());
        assertEquals(AuthErrorCode.EMAIL_TAKEN,
                assertThrows(BusinessException.class, () -> service.register(request)).errorCode());
    }

    @Test
    void loginUsesEmailAndRejectsUnknownOrWrongPassword() {
        User user = user(7L, null);
        user.setPasswordHash(encoder.encode("abcd1234"));
        when(users.findByEmail("sky@email.com")).thenReturn(user);
        when(tokens.createAccessToken(7L, "하늘", 0)).thenReturn("access");
        when(tokens.createRefreshToken(7L, 0)).thenReturn("refresh");
        assertEquals("access", service.login(new LoginRequest(" SKY@EMAIL.COM ", "abcd1234")).accessToken());
        assertEquals(AuthErrorCode.INVALID_CREDENTIALS,
                assertThrows(BusinessException.class,
                        () -> service.login(new LoginRequest("sky@email.com", "wrong"))).errorCode());
        assertEquals(AuthErrorCode.INVALID_CREDENTIALS,
                assertThrows(BusinessException.class,
                        () -> service.login(new LoginRequest("missing@test.com", "abcd1234"))).errorCode());
    }

    @Test
    void getMeHandlesMissingUserAndCharacter() {
        assertEquals(TokenErrorCode.INVALID_TOKEN,
                assertThrows(BusinessException.class, () -> service.getMe(9L)).errorCode());
        User user = user(7L, null);
        when(users.findById(7L)).thenReturn(user);
        var withoutCharacter = service.getMe(7L);
        assertNull(withoutCharacter.characterKey());
        assertNull(withoutCharacter.profileImageUrl());
        assertEquals("+09:00", withoutCharacter.createdAt().getOffset().toString());
        user.setCharacterId(3L);
        ProfileCharacter character = new ProfileCharacter();
        character.setSvgKey("gomdol");
        when(characters.findById(3L)).thenReturn(character);
        assertEquals("gomdol", service.getMe(7L).characterKey());
        user.setProfileImageUrl("profiles/photo.jpg");
        when(storage.publicUrl("profiles/photo.jpg")).thenReturn("/files/profiles/photo.jpg");
        assertEquals("/files/profiles/photo.jpg", service.getMe(7L).profileImageUrl());
    }

    @Test
    void changePasswordReissuesTokensAtIncrementedVersion() {
        User before = user(7L, null);
        before.setPasswordHash(encoder.encode("abcd1234"));
        User after = user(7L, null);
        after.setTokenVersion(1);
        when(users.findById(7L)).thenReturn(before, after);
        when(tokens.createAccessToken(7L, "하늘", 1)).thenReturn("new-access");
        when(tokens.createRefreshToken(7L, 1)).thenReturn("new-refresh");

        var response = service.changePassword(7L, new PasswordChangeRequest("abcd1234", "efgh5678"));
        assertEquals("new-access", response.accessToken());
        assertEquals("new-refresh", response.refreshToken());
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(users).changePassword(org.mockito.ArgumentMatchers.eq(7L), hash.capture());
        assertEquals(true, encoder.matches("efgh5678", hash.getValue()));
    }

    @Test
    void changePasswordRejectsMissingUserOrWrongCurrentPassword() {
        var request = new PasswordChangeRequest("wrong123", "efgh5678");
        assertEquals(TokenErrorCode.INVALID_TOKEN,
                assertThrows(BusinessException.class, () -> service.changePassword(7L, request)).errorCode());
        User user = user(7L, null);
        user.setPasswordHash(encoder.encode("abcd1234"));
        when(users.findById(7L)).thenReturn(user);
        assertEquals(AuthErrorCode.WRONG_CURRENT_PASSWORD,
                assertThrows(BusinessException.class, () -> service.changePassword(7L, request)).errorCode());
        verify(users, never()).changePassword(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refreshRejectsOldTokenVersion() {
        when(tokens.parseRefreshToken("old")).thenReturn(new TokenClaims(7L, 0));
        User user = user(7L, null);
        user.setTokenVersion(1);
        when(users.findById(7L)).thenReturn(user);
        assertEquals(TokenErrorCode.INVALID_TOKEN,
                assertThrows(BusinessException.class, () -> service.refresh(new RefreshRequest("old"))).errorCode());
    }

    private static User user(Long id, Long characterId) {
        User user = new User();
        user.setId(id);
        user.setEmail("sky@email.com");
        user.setNickname("하늘");
        user.setCharacterId(characterId);
        user.setCreatedAt(LocalDateTime.of(2026, 9, 28, 9, 0));
        return user;
    }
}
