package com.sapphire.hawe.withdraw.service;

import com.sapphire.hawe.auth.dao.UserMapper;
import com.sapphire.hawe.auth.domain.User;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.storage.FileStorage;
import com.sapphire.hawe.group.service.GroupErrorCode;
import com.sapphire.hawe.group.service.GroupService;
import com.sapphire.hawe.withdraw.dao.WithdrawMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WithdrawServiceTests {
    UserMapper users = mock(UserMapper.class);
    WithdrawMapper mapper = mock(WithdrawMapper.class);
    GroupService groups = mock(GroupService.class);
    FileStorage storage = mock(FileStorage.class);
    WithdrawService service = new WithdrawService(users, mapper, groups, storage);

    @Test void ownerCheckStopsBeforeDeletion() {
        when(users.findById(1L)).thenReturn(new User());
        when(mapper.existsOwnedGroupWithOtherMembers(1L)).thenReturn(true);
        assertEquals(WithdrawErrorCode.OWNER_MUST_TRANSFER,
                assertThrows(BusinessException.class, () -> service.withdraw(1L)).errorCode());
        InOrder order = inOrder(users, mapper);
        order.verify(users).findById(1L);
        order.verify(mapper).existsOwnedGroupWithOtherMembers(1L);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(storage);
    }

    @Test void leaveRaceBecomesOwnerError() {
        when(users.findById(1L)).thenReturn(new User());
        when(mapper.findSubmissionMediaKeys(1L)).thenReturn(List.of());
        when(mapper.findCommentImageKeysOnMySubmissions(1L)).thenReturn(List.of());
        when(mapper.findMyCommentImageKeys(1L)).thenReturn(List.of());
        when(mapper.findGroupIdsByUserId(1L)).thenReturn(List.of(9L));
        doThrow(new BusinessException(GroupErrorCode.SUCCESSOR_REQUIRED)).when(groups).leaveGroup(1L, 9L, null);
        assertEquals(WithdrawErrorCode.OWNER_MUST_TRANSFER,
                assertThrows(BusinessException.class, () -> service.withdraw(1L)).errorCode());
        verify(mapper, never()).deleteMySubmissions(any());
    }

    @Test void deletesDependenciesInOrderAndFilesAfterCommit() {
        User user = new User(); user.setProfileImageUrl("profiles/me.jpg");
        when(users.findById(1L)).thenReturn(user);
        when(mapper.findSubmissionMediaKeys(1L)).thenReturn(List.of("submissions/me.jpg"));
        when(mapper.findCommentImageKeysOnMySubmissions(1L)).thenReturn(List.of("comments/me.jpg"));
        when(mapper.findMyCommentImageKeys(1L)).thenReturn(List.of("comments/me.jpg"));
        when(mapper.findGroupIdsByUserId(1L)).thenReturn(List.of(7L, 8L));
        when(mapper.deleteUserById(1L)).thenReturn(1);
        doThrow(new IllegalStateException("missing file")).when(storage).delete("comments/me.jpg");
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.withdraw(1L);
            verifyNoInteractions(storage);
            InOrder order = inOrder(groups, mapper);
            order.verify(groups).leaveGroup(1L, 7L, null);
            order.verify(groups).leaveGroup(1L, 8L, null);
            order.verify(mapper).deleteMentionsOnMySubmissions(1L);
            order.verify(mapper).deleteCommentsOnMySubmissions(1L);
            order.verify(mapper).deleteReactionsOnMySubmissions(1L);
            order.verify(mapper).deleteGuessesOnMySubmissions(1L);
            order.verify(mapper).deleteMentionsOnMyComments(1L);
            order.verify(mapper).deleteMentionsOfMe(1L);
            order.verify(mapper).deleteMyComments(1L);
            order.verify(mapper).deleteMyReactions(1L);
            order.verify(mapper).deleteMyGuesses(1L);
            order.verify(mapper).deleteAiReviewsOnMySubmissions(1L);
            order.verify(mapper).deleteMySubmissions(1L);
            order.verify(mapper).deleteMyPokes(1L);
            order.verify(mapper).deleteMyNotifications(1L);
            order.verify(mapper).deleteUserById(1L);
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCommit();
            verify(storage).delete("submissions/me.jpg");
            verify(storage).delete("comments/me.jpg");
            verify(storage).delete("profiles/me.jpg");
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
}
