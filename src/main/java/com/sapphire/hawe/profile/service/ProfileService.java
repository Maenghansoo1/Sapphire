package com.sapphire.hawe.profile.service;

import com.sapphire.hawe.auth.dao.UserMapper;
import com.sapphire.hawe.auth.domain.User;
import com.sapphire.hawe.auth.dto.MeResponse;
import com.sapphire.hawe.auth.service.AuthService;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import com.sapphire.hawe.global.security.TokenErrorCode;
import com.sapphire.hawe.global.storage.FileStorage;
import com.sapphire.hawe.global.storage.FileTypeDetector;
import com.sapphire.hawe.profile.dto.ProfileImageResponse;
import com.sapphire.hawe.profile.dto.ProfileUpdateRequest;
import com.sapphire.hawe.submission.service.SubmissionErrorCode;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfileService {
    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);
    private final UserMapper users;
    private final AuthService auth;
    private final FileStorage storage;
    public ProfileService(UserMapper users, AuthService auth, FileStorage storage) {
        this.users = users; this.auth = auth; this.storage = storage;
    }

    @Transactional
    public MeResponse updateNickname(Long userId, ProfileUpdateRequest request) {
        requireUser(userId);
        users.updateNickname(userId, request.nickname());
        return auth.getMe(userId);
    }

    @Transactional
    public ProfileImageResponse uploadImage(Long userId, MultipartFile file) {
        User user = requireUser(userId);
        if (file == null || file.isEmpty()) invalid("사진 파일을 첨부해 주세요");
        if (file.getSize() > 10L * 1024 * 1024) throw new BusinessException(CommonErrorCode.FILE_TOO_LARGE);
        byte[] bytes;
        try { bytes = file.getBytes(); }
        catch (IOException e) { throw new IllegalStateException("파일을 읽을 수 없습니다", e); }
        if (bytes.length == 0) invalid("사진 파일을 첨부해 주세요");
        if (bytes.length > 10L * 1024 * 1024) throw new BusinessException(CommonErrorCode.FILE_TOO_LARGE);
        FileTypeDetector.Detected detected = FileTypeDetector.detect(bytes);
        if (detected == null || !"IMAGE".equals(detected.mediaType()))
            throw new BusinessException(SubmissionErrorCode.UNSUPPORTED_FILE_TYPE);
        String key = storage.store("profiles", bytes, detected.extension());
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) deleteQuietly(key);
                }
            });
        if (users.updateProfileImageUrl(userId, key) == 0 && !users.existsById(userId))
            throw new BusinessException(TokenErrorCode.INVALID_TOKEN);
        deleteAfterCommit(user.getProfileImageUrl());
        return new ProfileImageResponse(storage.publicUrl(key));
    }

    @Transactional
    public void deleteImage(Long userId) {
        User user = requireUser(userId);
        if (user.getProfileImageUrl() == null) return;
        if (users.updateProfileImageUrl(userId, null) == 0 && !users.existsById(userId))
            throw new BusinessException(TokenErrorCode.INVALID_TOKEN);
        deleteAfterCommit(user.getProfileImageUrl());
    }

    private User requireUser(Long id) {
        User user = users.findById(id);
        if (user == null) throw new BusinessException(TokenErrorCode.INVALID_TOKEN);
        return user;
    }

    private void deleteAfterCommit(String key) {
        if (key == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { deleteQuietly(key); }
            });
        else deleteQuietly(key);
    }

    private void deleteQuietly(String key) {
        try { storage.delete(key); }
        catch (RuntimeException error) { log.warn("Could not delete profile image", error); }
    }

    private static void invalid(String message) {
        throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, message);
    }
}
