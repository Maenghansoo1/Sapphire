package com.sapphire.hawe.group.service;

import com.sapphire.hawe.character.dao.CharacterMapper;
import com.sapphire.hawe.character.domain.ProfileCharacter;
import com.sapphire.hawe.character.service.CharacterErrorCode;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import com.sapphire.hawe.global.storage.FileStorage;
import com.sapphire.hawe.global.storage.FileTypeDetector;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.domain.GroupMember;
import com.sapphire.hawe.group.dto.GroupProfileRequest;
import com.sapphire.hawe.group.dto.MemberProfileResponse;
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
public class GroupProfileService {
    private static final Logger log = LoggerFactory.getLogger(GroupProfileService.class);
    private final GroupMapper groups;
    private final GroupMemberMapper members;
    private final CharacterMapper characters;
    private final FileStorage storage;

    public GroupProfileService(GroupMapper groups, GroupMemberMapper members,
            CharacterMapper characters, FileStorage storage) {
        this.groups = groups;
        this.members = members;
        this.characters = characters;
        this.storage = storage;
    }

    @Transactional
    public MemberProfileResponse updateCharacter(Long userId, Long groupId, GroupProfileRequest request) {
        GroupMember member = requireMember(groupId, userId);
        ProfileCharacter character = characters.findById(request.characterId());
        if (character == null) throw new BusinessException(CharacterErrorCode.CHARACTER_NOT_FOUND);
        members.setGroupProfile(groupId, userId, request.nickname(), request.characterId(), null);
        deleteAfterCommit(member.getProfileImageUrlOverride());
        return new MemberProfileResponse(userId, request.nickname(), character.getSvgKey(), null);
    }

    @Transactional
    public MemberProfileResponse updateImage(Long userId, Long groupId, String nickname, MultipartFile file) {
        GroupMember member = requireMember(groupId, userId);
        if (nickname == null || (nickname = nickname.trim()).length() < 2 || nickname.length() > 10)
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Nickname must be 2 to 10 characters");
        if (file == null || file.isEmpty())
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Attach an image file");
        if (file.getSize() > 10L * 1024 * 1024)
            throw new BusinessException(CommonErrorCode.FILE_TOO_LARGE);
        byte[] bytes;
        try { bytes = file.getBytes(); }
        catch (IOException e) { throw new IllegalStateException("Could not read image file", e); }
        if (bytes.length == 0)
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Attach an image file");
        if (bytes.length > 10L * 1024 * 1024)
            throw new BusinessException(CommonErrorCode.FILE_TOO_LARGE);
        FileTypeDetector.Detected detected = FileTypeDetector.detect(bytes);
        if (detected == null || !"IMAGE".equals(detected.mediaType()))
            throw new BusinessException(SubmissionErrorCode.UNSUPPORTED_FILE_TYPE);
        String key = storage.store("group-profiles", bytes, detected.extension());
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) deleteQuietly(key);
                }
            });
        members.setGroupProfile(groupId, userId, nickname, null, key);
        deleteAfterCommit(member.getProfileImageUrlOverride());
        return new MemberProfileResponse(userId, nickname, null, storage.publicUrl(key));
    }

    @Transactional
    public void delete(Long userId, Long groupId) {
        GroupMember member = requireMember(groupId, userId);
        members.setGroupProfile(groupId, userId, null, null, null);
        deleteAfterCommit(member.getProfileImageUrlOverride());
    }

    private GroupMember requireMember(Long groupId, Long userId) {
        if (groups.findById(groupId) == null) throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        GroupMember member = members.findByGroupIdAndUserId(groupId, userId);
        if (member == null) throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        return member;
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
        catch (RuntimeException error) { log.warn("Could not delete group profile image", error); }
    }
}
