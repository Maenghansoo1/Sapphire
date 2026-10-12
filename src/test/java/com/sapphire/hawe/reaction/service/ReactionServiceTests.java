package com.sapphire.hawe.reaction.service;

import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.reaction.dao.ReactionMapper;
import com.sapphire.hawe.reaction.dao.ReactionRow;
import com.sapphire.hawe.reaction.dto.ReactionRequest;
import com.sapphire.hawe.submission.service.SubmissionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReactionServiceTests {
    ReactionMapper mapper = mock(ReactionMapper.class);
    SubmissionService submissions = mock(SubmissionService.class);
    ReactionService service = new ReactionService(mapper, submissions);

    @Test void invalidStickerDoesNotReadDatabase() {
        for (String value : List.of("HEART", "heart ", "unknown", "")) {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.toggle(1L, 2L, new ReactionRequest(value)));
            assertEquals("VALIDATION_ERROR", error.errorCode().code());
        }
        verifyNoInteractions(mapper, submissions);
    }

    @Test void accessFailureStopsToggle() {
        doThrow(new BusinessException(com.sapphire.hawe.submission.service.SubmissionErrorCode.FEED_LOCKED))
                .when(submissions).requireAccessible(1L, 2L);
        assertEquals("FEED_LOCKED", assertThrows(BusinessException.class,
                () -> service.toggle(1L, 2L, new ReactionRequest("heart"))).errorCode().code());
        verifyNoInteractions(mapper);
    }

    @Test void toggleInsertsOrDeletesAndIgnoresConcurrentDuplicate() {
        service.toggle(1L, 2L, new ReactionRequest("heart"));
        verify(mapper).insert(2L, 1L, "heart");
        when(mapper.delete(2L, 1L, "heart")).thenReturn(1);
        service.toggle(1L, 2L, new ReactionRequest("heart"));
        verify(mapper, times(1)).insert(2L, 1L, "heart");
        when(mapper.delete(2L, 1L, "heart")).thenReturn(0);
        doThrow(new DuplicateKeyException("duplicate")).when(mapper).insert(2L, 1L, "heart");
        assertEquals(List.of(), service.toggle(1L, 2L, new ReactionRequest("heart")).reactions());
    }

    @Test void summariesUseEnumOrderAndInsertionOrderWithinType() {
        var summaries = ReactionService.summarize(List.of(row(2L, "fire"), row(3L, "heart"),
                row(1L, "heart"), row(4L, "unknown")), 1L);
        assertEquals(List.of("heart", "fire"), summaries.stream().map(s -> s.stickerType()).toList());
        assertEquals(List.of(3L, 1L), summaries.getFirst().userIds());
        assertEquals(2, summaries.getFirst().count());
        assertTrue(summaries.getFirst().reactedByMe());
        assertFalse(summaries.get(1).reactedByMe());
        assertEquals(List.of(), ReactionService.summarize(List.of(), 1L));
    }

    private static ReactionRow row(Long userId, String type) {
        ReactionRow row = new ReactionRow();
        row.setUserId(userId); row.setStickerType(type);
        return row;
    }
}
