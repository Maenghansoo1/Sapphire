package com.sapphire.hawe.comment.service;

import com.sapphire.hawe.comment.dao.CommentInsertRow;
import com.sapphire.hawe.comment.dao.CommentMapper;
import com.sapphire.hawe.comment.dao.CommentRow;
import com.sapphire.hawe.comment.domain.CommentCreatedEvent;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.storage.FileStorage;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.submission.dao.SubmissionRow;
import com.sapphire.hawe.submission.service.SubmissionService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommentServiceTests {
    CommentMapper comments = mock(CommentMapper.class);
    SubmissionService submissions = mock(SubmissionService.class);
    GroupMemberMapper members = mock(GroupMemberMapper.class);
    FileStorage storage = mock(FileStorage.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    CommentService service = new CommentService(comments, submissions, members, storage, events, new tools.jackson.databind.ObjectMapper());

    @BeforeEach void setUp() {
        SubmissionRow submission = new SubmissionRow();
        submission.setId(10L); submission.setGroupId(11L); submission.setMissionId(9L); submission.setUserId(4L);
        when(submissions.requireAccessible(3L, 10L)).thenReturn(submission);
    }

    @Test void contentAndFileRules() {
        assertError("VALIDATION_ERROR", () -> service.create(3L, 10L, null, null, null));
        assertError("VALIDATION_ERROR", () -> service.create(3L, 10L, "  ", null, null));
        assertError("VALIDATION_ERROR", () -> service.create(3L, 10L, "a".repeat(301), null, null));
        assertError("VALIDATION_ERROR", () -> service.create(3L, 10L, null, null,
                new MockMultipartFile("file", new byte[0])));
        assertError("UNSUPPORTED_FILE_TYPE", () -> service.create(3L, 10L, "hello", null,
                new MockMultipartFile("file", "a.jpg", "image/jpeg", "fake".getBytes())));
        assertError("FILE_TOO_LARGE", () -> service.create(3L, 10L, "hello", null,
                new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[10 * 1024 * 1024 + 1])));
        verify(storage, never()).store(anyString(), any(), anyString());
    }

    @Test void imageOnlyStoresEmptyContentAndDeduplicatedMentions() {
        when(members.existsByGroupIdAndUserId(11L, 4L)).thenReturn(true);
        when(storage.store(eq("comments"), any(), eq("jpg"))).thenReturn("comments/a.jpg");
        when(storage.publicUrl("comments/a.jpg")).thenReturn("/files/comments/a.jpg");
        doAnswer(call -> { call.getArgument(0, CommentInsertRow.class).setId(1L); return null; })
                .when(comments).insert(any());
        CommentRow stored = row(1L, 3L, "", "comments/a.jpg");
        when(comments.findDetailById(1L)).thenReturn(stored);
        var response = service.create(3L, 10L, "   ", List.of(4L, 4L), jpg());
        assertEquals("", response.content());
        assertEquals("/files/comments/a.jpg", response.imageUrl());
        assertEquals(List.of(4L), response.mentionedUserIds());
        verify(comments, times(1)).insertMention(1L, 4L);
        var inserted = org.mockito.ArgumentCaptor.forClass(CommentInsertRow.class);
        verify(comments).insert(inserted.capture());
        assertEquals("", inserted.getValue().getContent());
        verify(events).publishEvent(new CommentCreatedEvent(1L, 11L, 9L, 10L, 3L, 4L, List.of(4L)));
    }

    @Test void invalidMentionDoesNotStoreFile() {
        assertError("VALIDATION_ERROR", () -> service.create(3L, 10L, "hello", List.of(5L), jpg()));
        verify(storage, never()).store(anyString(), any(), anyString());
        verify(comments, never()).insert(any());
    }

    @Test void listExposesMascotDetailAndOmitsItForPeople() {
        CommentRow human = row(1L, 4L, "안녕", null);
        CommentRow mascot = row(2L, 7L, "고양이인가요?", null);
        mascot.setAi(true);
        mascot.setAiDetail("{\"kind\":\"DRAW_GUESS\",\"guesses\":[{\"label\":\"고양이\",\"probability\":0.62}]}");
        when(comments.findPageBySubmissionId(10L, 0, 20)).thenReturn(List.of(human, mascot));
        var result = service.list(3L, 10L, 0, 20).content();
        assertFalse(result.getFirst().ai());
        assertNull(result.getFirst().aiDetail());
        assertTrue(result.get(1).ai());
        assertFalse(result.get(1).mine());
        assertEquals("DRAW_GUESS", result.get(1).aiDetail().path("kind").asString());
        String encoded = new tools.jackson.databind.ObjectMapper().writeValueAsString(result.getFirst());
        assertFalse(encoded.contains("aiDetail"));
    }

    @Test void deletionChecksExistenceAccessAndOwnerBeforeDeleting() {
        assertError("COMMENT_NOT_FOUND", () -> service.delete(3L, 99L));
        verify(submissions, never()).requireAccessible(3L, 99L);
        CommentRow foreign = row(1L, 4L, "hi", "comments/a.jpg");
        when(comments.findDetailById(1L)).thenReturn(foreign);
        assertError("NOT_COMMENT_OWNER", () -> service.delete(3L, 1L));
        verify(comments, never()).deleteById(anyLong());
        when(comments.findDetailById(1L)).thenReturn(row(1L, 3L, "hi", "comments/a.jpg"));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.delete(3L, 1L);
            verify(comments).deleteMentionsByCommentId(1L);
            verify(comments).deleteById(1L);
            verify(storage, never()).delete("comments/a.jpg");
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(storage).delete("comments/a.jpg");
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    private static void assertError(String code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(BusinessException.class, action).errorCode().code());
    }
    private static MockMultipartFile jpg() {
        return new MockMultipartFile("file", "a.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1});
    }
    private static CommentRow row(Long id, Long userId, String content, String key) {
        CommentRow row = new CommentRow();
        row.setId(id); row.setSubmissionId(10L); row.setUserId(userId);
        row.setNickname("Test"); row.setContent(content); row.setImageUrl(key);
        row.setCreatedAt(LocalDateTime.now());
        return row;
    }
}
