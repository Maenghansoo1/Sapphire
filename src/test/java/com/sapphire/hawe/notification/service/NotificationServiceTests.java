package com.sapphire.hawe.notification.service;

import com.sapphire.hawe.comment.domain.CommentCreatedEvent;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.storage.FileStorage;
import com.sapphire.hawe.mission.domain.MissionsPublishedEvent;
import com.sapphire.hawe.notification.dao.NotificationMapper;
import com.sapphire.hawe.notification.dao.NotificationRow;
import com.sapphire.hawe.poke.domain.PokeSentEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationServiceTests {
    NotificationMapper mapper = mock(NotificationMapper.class);
    FileStorage storage = mock(FileStorage.class);
    NotificationService service = new NotificationService(mapper, storage);

    @Test void listBuildsAllBodiesAndClampsPage() {
        when(mapper.count(8L)).thenReturn(4L);
        when(mapper.findPage(8L, 0, 1)).thenReturn(List.of(row("topic"), row("poke"),
                row("comment"), row("mention")));
        when(storage.publicUrl("profiles/a.png")).thenReturn("/files/profiles/a.png");
        var page = service.list(8L, -3, 0);
        assertEquals(0, page.page());
        assertEquals(1, page.size());
        assertEquals(4, page.totalPages());
        assertEquals(List.of("오늘의 주제가 도착했어요", "서연님이 콕 찔렀어요",
                "서연님이 내 작품에 댓글을 남겼어요", "서연님이 댓글에서 나를 언급했어요"),
                page.content().stream().map(item -> item.body()).toList());
        assertNull(page.content().getFirst().fromUser());
        assertEquals("/files/profiles/a.png", page.content().get(1).fromUser().profileImageUrl());
        assertEquals("+09:00", page.content().getFirst().createdAt().getOffset().toString());
        service.list(8L, 2, 999);
        verify(mapper).findPage(8L, 200, 100);
    }

    @Test void readIsOwnedAndIdempotent() {
        when(mapper.markRead(1L, 8L)).thenReturn(0);
        when(mapper.existsOwned(1L, 8L)).thenReturn(true);
        assertDoesNotThrow(() -> service.read(8L, 1L));
        when(mapper.existsOwned(1L, 8L)).thenReturn(false);
        assertEquals(NotificationErrorCode.NOTIFICATION_NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.read(8L, 1L)).errorCode());
        service.readAll(8L);
        verify(mapper).markAllRead(8L);
        when(mapper.countUnread(8L)).thenReturn(3L);
        assertEquals(3, service.unreadCount(8L).unreadCount());
    }

    @Test void creationRespectsRecipientsAndMentionPriority() {
        service.createPoke(new PokeSentEvent(10L, 20L, 1L, 2L));
        service.createPoke(new PokeSentEvent(10L, 20L, 1L, 1L));
        service.createComment(new CommentCreatedEvent(4L, 10L, 20L, 30L, 1L, 2L,
                List.of(2L, 2L, 1L, 3L)));
        service.createComment(new CommentCreatedEvent(5L, 10L, 20L, 31L, 1L, 2L, List.of()));
        service.createTopics(new MissionsPublishedEvent(10L, List.of(20L, 21L), List.of(1L, 2L)));
        ArgumentCaptor<String> types = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> recipients = ArgumentCaptor.forClass(Long.class);
        verify(mapper, times(8)).insert(nullable(Long.class), recipients.capture(), types.capture(),
                eq(10L), anyLong(), nullable(Long.class), any(LocalDateTime.class));
        assertEquals(List.of("poke", "mention", "mention", "comment", "topic", "topic", "topic", "topic"),
                types.getAllValues());
        assertEquals(List.of(2L, 2L, 3L, 2L, 1L, 2L, 1L, 2L), recipients.getAllValues());
    }

    private static NotificationRow row(String type) {
        NotificationRow row = new NotificationRow();
        row.setId(1L); row.setType(type); row.setGroupId(10L); row.setGroupName("그룹");
        if (!"topic".equals(type)) {
            row.setFromUserId(1L); row.setNickname("서연"); row.setProfileImageUrl("profiles/a.png");
        }
        row.setMissionId(20L); row.setCreatedAt(LocalDateTime.of(2026, 10, 19, 12, 0));
        return row;
    }
}
