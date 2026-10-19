package com.sapphire.hawe.withdraw.service;

import com.sapphire.hawe.auth.dao.UserMapper;
import com.sapphire.hawe.auth.domain.User;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.security.TokenErrorCode;
import com.sapphire.hawe.global.storage.FileStorage;
import com.sapphire.hawe.group.service.GroupErrorCode;
import com.sapphire.hawe.group.service.GroupService;
import com.sapphire.hawe.notification.dao.NotificationMapper;
import com.sapphire.hawe.withdraw.dao.WithdrawMapper;
import java.util.LinkedHashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class WithdrawService {
    private static final Logger log = LoggerFactory.getLogger(WithdrawService.class);
    private final UserMapper users;
    private final WithdrawMapper withdraw;
    private final GroupService groups;
    private final FileStorage storage;
    private final NotificationMapper notifications;
    public WithdrawService(UserMapper users, WithdrawMapper withdraw, GroupService groups, FileStorage storage,
            NotificationMapper notifications) {
        this.users = users; this.withdraw = withdraw; this.groups = groups; this.storage = storage;
        this.notifications = notifications;
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = users.findById(userId);
        if (user == null) throw new BusinessException(TokenErrorCode.INVALID_TOKEN);
        if (withdraw.existsOwnedGroupWithOtherMembers(userId))
            throw new BusinessException(WithdrawErrorCode.OWNER_MUST_TRANSFER);

        LinkedHashSet<String> keys = new LinkedHashSet<>();
        List<Long> submissionIds = withdraw.findSubmissionIdsByUserId(userId);
        keys.addAll(withdraw.findSubmissionMediaKeys(userId));
        keys.addAll(withdraw.findCommentImageKeysOnMySubmissions(userId));
        keys.addAll(withdraw.findMyCommentImageKeys(userId));
        if (user.getProfileImageUrl() != null) keys.add(user.getProfileImageUrl());

        for (Long groupId : withdraw.findGroupIdsByUserId(userId)) {
            try { groups.leaveGroup(userId, groupId, null); }
            catch (BusinessException error) {
                if (error.errorCode() == GroupErrorCode.SUCCESSOR_REQUIRED)
                    throw new BusinessException(WithdrawErrorCode.OWNER_MUST_TRANSFER);
                throw error;
            }
        }

        withdraw.deleteInsightsInvolvingUser(userId);
        withdraw.deleteMentionsOnMySubmissions(userId);
        withdraw.deleteCommentsOnMySubmissions(userId);
        withdraw.deleteReactionsOnMySubmissions(userId);
        withdraw.deleteGuessesOnMySubmissions(userId);
        withdraw.deleteMentionsOnMyComments(userId);
        withdraw.deleteMentionsOfMe(userId);
        withdraw.deleteMyComments(userId);
        withdraw.deleteMyReactions(userId);
        withdraw.deleteMyGuesses(userId);
        withdraw.deleteAiReviewsOnMySubmissions(userId);
        if (!submissionIds.isEmpty()) notifications.deleteBySubmissionIds(submissionIds);
        withdraw.deleteMySubmissions(userId);
        withdraw.deleteMyPokes(userId);
        withdraw.deleteMyNotifications(userId);
        if (withdraw.deleteUserById(userId) == 0) throw new BusinessException(TokenErrorCode.INVALID_TOKEN);

        Runnable cleanup = () -> {
            for (String key : keys) {
                try { storage.delete(key); }
                catch (RuntimeException error) { log.warn("Could not delete withdrawn user's file {}", key, error); }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { cleanup.run(); }
            });
        else cleanup.run();
    }
}
