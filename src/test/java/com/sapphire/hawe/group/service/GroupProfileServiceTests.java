package com.sapphire.hawe.group.service;

import com.sapphire.hawe.character.dao.CharacterMapper;
import com.sapphire.hawe.character.domain.ProfileCharacter;
import com.sapphire.hawe.character.service.CharacterErrorCode;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import com.sapphire.hawe.global.storage.FileStorage;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.group.domain.GroupMember;
import com.sapphire.hawe.group.dto.GroupProfileRequest;
import com.sapphire.hawe.submission.service.SubmissionErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupProfileServiceTests {
    @Mock GroupMapper groups;
    @Mock GroupMemberMapper members;
    @Mock CharacterMapper characters;
    @Mock FileStorage storage;
    GroupProfileService service;

    @BeforeEach void setUp() { service = new GroupProfileService(groups, members, characters, storage); }

    @Test void checksGroupAndMembershipBeforeChangingAnything() {
        assertEquals(GroupErrorCode.GROUP_NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.delete(1L, 2L)).errorCode());
        when(groups.findById(2L)).thenReturn(new Group());
        assertEquals(GroupErrorCode.NOT_GROUP_MEMBER,
                assertThrows(BusinessException.class, () -> service.delete(1L, 2L)).errorCode());
        verifyNoInteractions(storage);
        verify(members, never()).setGroupProfile(any(), any(), any(), any(), any());
    }

    @Test void missingCharacterDoesNotUpdate() {
        member(null);
        assertEquals(CharacterErrorCode.CHARACTER_NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.updateCharacter(1L, 2L,
                        new GroupProfileRequest("Group", 99L))).errorCode());
        verify(members, never()).setGroupProfile(any(), any(), any(), any(), any());
    }

    @Test void imageValidationPrecedesStorageAndUpdate() {
        member(null);
        var bad = new MockMultipartFile("file", "bad.txt", "text/plain", new byte[]{1, 2});
        assertEquals(CommonErrorCode.VALIDATION_ERROR,
                assertThrows(BusinessException.class, () -> service.updateImage(1L, 2L, " ", bad)).errorCode());
        assertEquals(CommonErrorCode.VALIDATION_ERROR,
                assertThrows(BusinessException.class, () -> service.updateImage(1L, 2L, "Group", null)).errorCode());
        assertEquals(SubmissionErrorCode.UNSUPPORTED_FILE_TYPE,
                assertThrows(BusinessException.class, () -> service.updateImage(1L, 2L, "Group", bad)).errorCode());
        var oversized = new MockMultipartFile("file", "huge.jpg", "image/jpeg", new byte[10 * 1024 * 1024 + 1]);
        assertEquals(CommonErrorCode.FILE_TOO_LARGE,
                assertThrows(BusinessException.class, () -> service.updateImage(1L, 2L, "Group", oversized)).errorCode());
        verifyNoInteractions(storage);
        verify(members, never()).setGroupProfile(any(), any(), any(), any(), any());
    }

    @Test void characterAndImageResponsesUseChosenAvatar() {
        member(null);
        ProfileCharacter character = new ProfileCharacter();
        character.setSvgKey("gomdol");
        when(characters.findById(3L)).thenReturn(character);
        var chosen = service.updateCharacter(1L, 2L, new GroupProfileRequest("Group", 3L));
        assertEquals("gomdol", chosen.characterKey());
        assertNull(chosen.profileImageUrl());
        verify(members).setGroupProfile(2L, 1L, "Group", 3L, null);

        var jpg = new MockMultipartFile("file", "photo.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2});
        when(storage.store(eq("group-profiles"), any(byte[].class), eq("jpg"))).thenReturn("group-profiles/new.jpg");
        when(storage.publicUrl("group-profiles/new.jpg")).thenReturn("/files/group-profiles/new.jpg");
        var photo = service.updateImage(1L, 2L, " Photo ", jpg);
        assertNull(photo.characterKey());
        assertEquals("/files/group-profiles/new.jpg", photo.profileImageUrl());
        verify(members).setGroupProfile(2L, 1L, "Photo", null, "group-profiles/new.jpg");
    }

    @Test void deletingAbsentProfileIsIdempotent() {
        member(null);
        service.delete(1L, 2L);
        service.delete(1L, 2L);
        verify(members, times(2)).setGroupProfile(2L, 1L, null, null, null);
        verifyNoInteractions(storage);
    }

    @Test void oldImageIsDeletedOnlyAfterCommit() {
        member("group-profiles/old.jpg");
        ProfileCharacter character = new ProfileCharacter();
        character.setSvgKey("gomdol");
        when(characters.findById(3L)).thenReturn(character);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.updateCharacter(1L, 2L, new GroupProfileRequest("Group", 3L));
            verify(storage, never()).delete(any());
            for (var sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCommit();
            verify(storage).delete("group-profiles/old.jpg");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test void newlyStoredImageIsDeletedOnRollbackAndOldImageIsKept() {
        member("group-profiles/old.jpg");
        var jpg = new MockMultipartFile("file", "photo.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2});
        when(storage.store(eq("group-profiles"), any(byte[].class), eq("jpg")))
                .thenReturn("group-profiles/new.jpg");
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.updateImage(1L, 2L, "Photo", jpg);
            verify(storage, never()).delete(any());
            for (var sync : TransactionSynchronizationManager.getSynchronizations())
                sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            verify(storage).delete("group-profiles/new.jpg");
            verify(storage, never()).delete("group-profiles/old.jpg");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void member(String imageKey) {
        when(groups.findById(2L)).thenReturn(new Group());
        GroupMember member = new GroupMember();
        member.setProfileImageUrlOverride(imageKey);
        when(members.findByGroupIdAndUserId(2L, 1L)).thenReturn(member);
    }
}
