package com.sapphire.hawe.submission.service;

import com.sapphire.hawe.global.config.VisionProperties;
import com.sapphire.hawe.global.storage.FileStorage;
import com.sapphire.hawe.mission.dao.MissionMapper;
import com.sapphire.hawe.mission.dao.MissionRow;
import com.sapphire.hawe.mascot.service.MascotService;
import com.sapphire.hawe.submission.dao.AiReviewMapper;
import com.sapphire.hawe.submission.dao.SubmissionMapper;
import com.sapphire.hawe.submission.dao.SubmissionRow;
import java.time.Duration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;

class TopicFitServiceTests {
    SubmissionMapper submissions = mock(SubmissionMapper.class);
    MissionMapper missions = mock(MissionMapper.class);
    AiReviewMapper reviews = mock(AiReviewMapper.class);
    FileStorage storage = mock(FileStorage.class);
    VisionClient client = mock(VisionClient.class);
    VisionProperties properties = new VisionProperties(true, null, null, null, Duration.ZERO, null, null);
    MascotService mascot = mock(MascotService.class);
    TopicFitService service = new TopicFitService(submissions, missions, reviews, storage, client, properties, new ObjectMapper(), mascot);
    SubmissionRow row = new SubmissionRow();
    MissionRow mission = new MissionRow();

    @BeforeEach void setUp() {
        row.setId(1L); row.setMissionId(2L); row.setUserId(3L);
        row.setTemplate("PHOTO_NOW"); row.setMediaUrl("submissions/photo.jpg");
        mission.setParams("{\"subject\":\"창밖 풍경\"}");
        when(submissions.findDetailById(1L)).thenReturn(row);
        when(missions.findById(2L, 3L)).thenReturn(mission);
        when(reviews.insertPendingIfAbsent(1L)).thenReturn(1);
        when(storage.load("submissions/photo.jpg")).thenReturn(new byte[] {1});
    }

    @Test void successStoresScoreButOnlyVerdictIsPublic() {
        when(client.call(eq("PHOTO_NOW"), eq("창밖 풍경"), any(), eq("photo.jpg")))
                .thenReturn(new TopicFitResult(0.5, "model", "1"));
        service.review(1L);
        verify(reviews).complete(eq(1L), eq("DONE"), eq(0.5), eq("PERFECT_FIT"), anyString(), eq("model/1"), eq(1));
        verify(mascot).commentPhotoScore(eq(1L), anyString(), eq(100));
    }

    @Test void lowFitStoresEncouragingCommentWithoutBadge() {
        when(client.call(eq("PHOTO_NOW"), eq("창밖 풍경"), any(), eq("photo.jpg")))
                .thenReturn(new TopicFitResult(0.016, "model", "1"));
        service.review(1L);
        verify(reviews).complete(1L, "DONE", 0.016, null, "오늘의 한 장, 잘 남겼어요", "model/1", 1);
        verify(mascot).commentPhotoScore(1L, "오늘의 한 장, 잘 남겼어요", new TopicFitJudge().score(0.016, properties.photoNow()));
    }

    @Test void failureRetriesOnceAndCompletesFailed() {
        when(client.call(anyString(), anyString(), any(), anyString())).thenThrow(new VisionException("failed"));
        service.review(1L);
        verify(client, times(2)).call(anyString(), anyString(), any(), anyString());
        verify(reviews).complete(1L, "FAILED", null, null, null, null, 2);
        verifyNoInteractions(mascot);
    }

    @Test void doneWriteFailureDoesNotRepeatVisionCall() {
        when(client.call(anyString(), anyString(), any(), anyString()))
                .thenReturn(new TopicFitResult(0.5, "model", "1"));
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .when(reviews).complete(eq(1L), eq("DONE"), anyDouble(), anyString(), anyString(), anyString(), eq(1));
        service.review(1L);
        verify(client, times(1)).call(anyString(), anyString(), any(), anyString());
        verify(reviews, never()).complete(eq(1L), eq("FAILED"), any(), any(), any(), any(), anyInt());
    }

    @Test void invalidTopicSkipsWithoutCallingVision() {
        mission.setParams("{\"subject\":\"x\"}");
        service.review(1L);
        verify(reviews).complete(1L, "SKIPPED", null, null, null, null, 0);
        verifyNoInteractions(client);
        verifyNoInteractions(mascot);
    }

    @Test void missingSubmissionAndDuplicateEventDoNothing() {
        when(submissions.findDetailById(1L)).thenReturn(null);
        service.review(1L);
        verifyNoInteractions(reviews, client);
        when(submissions.findDetailById(1L)).thenReturn(row);
        when(reviews.insertPendingIfAbsent(1L)).thenReturn(0);
        service.review(1L);
        verifyNoInteractions(client);
    }
}
