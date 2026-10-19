package com.sapphire.hawe.mascot.service;

import com.sapphire.hawe.comment.dao.CommentInsertRow;
import com.sapphire.hawe.comment.dao.CommentMapper;
import com.sapphire.hawe.mascot.dao.MascotMapper;
import com.sapphire.hawe.reaction.dao.ReactionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MascotServiceTests {
    MascotMapper mapper = mock(MascotMapper.class);
    CommentMapper comments = mock(CommentMapper.class);
    ReactionMapper reactions = mock(ReactionMapper.class);
    MascotService service = new MascotService(mapper, comments, reactions, new ObjectMapper(), () -> 3);

    @Test void reactionUsesOnlyAllowedStickersAndMissingMascotSkips() {
        when(mapper.findUserId()).thenReturn(7L);
        service.reactRandomly(10L);
        verify(reactions).existsBySubmissionIdAndUserId(10L, 7L);
        verify(reactions).insert(10L, 7L, "fire");
        verify(mapper).findUserId();

        MascotMapper absent = mock(MascotMapper.class);
        when(absent.findUserId()).thenReturn(null);
        MascotService missing = new MascotService(absent, comments, reactions, new ObjectMapper(), () -> 0);
        missing.reactRandomly(11L);
        verifyNoMoreInteractions(reactions);
    }

    @Test void guessIsSanitizedSortedAndLimited() throws Exception {
        when(mapper.findUserId()).thenReturn(7L);
        service.commentDrawGuess(10L, "[{\"label\":\"강아지\",\"probability\":0.2},"
                + "{\"label\":\"고양이\",\"probability\":0.8},"
                + "{\"label\":\"나무\",\"probability\":0.4},"
                + "{\"label\":\"별\",\"probability\":0.1},"
                + "{\"label\":\"bad\\nlabel\",\"probability\":0.99},"
                + "{\"label\":\"새\",\"probability\":1.2}]");
        ArgumentCaptor<CommentInsertRow> saved = ArgumentCaptor.forClass(CommentInsertRow.class);
        verify(comments).insert(saved.capture());
        assertEquals("고양이인가요?", saved.getValue().getContent());
        var detail = new ObjectMapper().readTree(saved.getValue().getAiDetail());
        assertEquals("DRAW_GUESS", detail.path("kind").asString());
        assertEquals(3, detail.path("guesses").size());
        assertEquals("고양이", detail.path("guesses").get(0).path("label").asString());
    }

    @Test void invalidGuessAndDuplicateCommentAreSkipped() {
        when(mapper.findUserId()).thenReturn(7L);
        service.commentDrawGuess(10L, "[{\"label\":\"bad\\nlabel\",\"probability\":0.8}]");
        verifyNoInteractions(comments);
        when(comments.existsBySubmissionIdAndUserId(10L, 7L)).thenReturn(true);
        service.commentPhotoScore(10L, "좋아요", 73);
        verify(comments, never()).insert(any());
    }

    @Test void photoScoreStoresStructuredDetail() throws Exception {
        when(mapper.findUserId()).thenReturn(7L);
        service.commentPhotoScore(10L, "오늘 주제가 잘 느껴져요", 73);
        ArgumentCaptor<CommentInsertRow> saved = ArgumentCaptor.forClass(CommentInsertRow.class);
        verify(comments).insert(saved.capture());
        assertEquals(73, new ObjectMapper().readTree(saved.getValue().getAiDetail()).path("score").asInt());
    }
}
