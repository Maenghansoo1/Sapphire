package com.sapphire.hawe.profile.service;

import com.sapphire.hawe.auth.dao.UserMapper;
import com.sapphire.hawe.auth.domain.User;
import com.sapphire.hawe.auth.dto.MeResponse;
import com.sapphire.hawe.auth.service.AuthService;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import com.sapphire.hawe.global.storage.FileStorage;
import com.sapphire.hawe.profile.dto.ProfileUpdateRequest;
import com.sapphire.hawe.submission.service.SubmissionErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfileServiceTests {
    UserMapper users = mock(UserMapper.class);
    AuthService auth = mock(AuthService.class);
    FileStorage storage = mock(FileStorage.class);
    ProfileService service = new ProfileService(users, auth, storage);

    @Test void nicknameReturnsMeAndImageValidationDoesNotStore() {
        User user = new User(); user.setId(1L);
        when(users.findById(1L)).thenReturn(user);
        MeResponse expected = mock(MeResponse.class);
        when(auth.getMe(1L)).thenReturn(expected);
        assertSame(expected, service.updateNickname(1L, new ProfileUpdateRequest(" New ")));
        verify(users).updateNickname(1L, "New");
        var empty = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[0]);
        assertEquals(CommonErrorCode.VALIDATION_ERROR,
                assertThrows(BusinessException.class, () -> service.uploadImage(1L, empty)).errorCode());
        var bad = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2});
        assertEquals(SubmissionErrorCode.UNSUPPORTED_FILE_TYPE,
                assertThrows(BusinessException.class, () -> service.uploadImage(1L, bad)).errorCode());
        MultipartFile huge = mock(MultipartFile.class);
        when(huge.getSize()).thenReturn(10L * 1024 * 1024 + 1);
        assertEquals(CommonErrorCode.FILE_TOO_LARGE,
                assertThrows(BusinessException.class, () -> service.uploadImage(1L, huge)).errorCode());
        verifyNoInteractions(storage);
    }

    @Test void replacementDeletesOldFileOnlyAfterCommitAndRollBackDeletesNewFile() {
        User user = new User(); user.setProfileImageUrl("profiles/old.jpg");
        when(users.findById(1L)).thenReturn(user);
        when(storage.store(eq("profiles"), any(), eq("jpg"))).thenReturn("profiles/new.jpg");
        when(users.updateProfileImageUrl(1L, "profiles/new.jpg")).thenReturn(1);
        when(storage.publicUrl("profiles/new.jpg")).thenReturn("/files/profiles/new.jpg");
        var jpg = new MockMultipartFile("file", "a.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1});
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertEquals("/files/profiles/new.jpg", service.uploadImage(1L, jpg).profileImageUrl());
            verify(storage, never()).delete(any());
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCommit();
            verify(storage).delete("profiles/old.jpg");
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations())
                sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            verify(storage).delete("profiles/new.jpg");
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test void deleteWithoutImageIsIdempotent() {
        when(users.findById(1L)).thenReturn(new User());
        service.deleteImage(1L);
        verify(users, never()).updateProfileImageUrl(any(), any());
    }
}
