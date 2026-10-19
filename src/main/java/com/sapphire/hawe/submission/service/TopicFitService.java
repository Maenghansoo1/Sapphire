package com.sapphire.hawe.submission.service;

import com.sapphire.hawe.global.config.VisionProperties;
import com.sapphire.hawe.global.storage.FileStorage;
import com.sapphire.hawe.mission.dao.MissionMapper;
import com.sapphire.hawe.mission.dao.MissionRow;
import com.sapphire.hawe.mascot.service.MascotService;
import com.sapphire.hawe.submission.dao.AiReviewMapper;
import com.sapphire.hawe.submission.dao.SubmissionMapper;
import com.sapphire.hawe.submission.dao.SubmissionRow;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class TopicFitService {
    private static final Logger log = LoggerFactory.getLogger(TopicFitService.class);
    private final SubmissionMapper submissions;
    private final MissionMapper missions;
    private final AiReviewMapper reviews;
    private final FileStorage storage;
    private final VisionClient client;
    private final VisionProperties properties;
    private final ObjectMapper json;
    private final MascotService mascot;
    private final TopicFitJudge judge = new TopicFitJudge();

    public TopicFitService(SubmissionMapper submissions, MissionMapper missions, AiReviewMapper reviews,
            FileStorage storage, VisionClient client, VisionProperties properties, ObjectMapper json, MascotService mascot) {
        this.submissions = submissions;
        this.missions = missions;
        this.reviews = reviews;
        this.storage = storage;
        this.client = client;
        this.properties = properties;
        this.json = json;
        this.mascot = mascot;
    }

    public void review(Long submissionId) {
        try {
            SubmissionRow row = submissions.findDetailById(submissionId);
            if (row == null || properties.thresholdFor(row.getTemplate()) == null || row.getMediaUrl() == null) return;
            if (reviews.insertPendingIfAbsent(submissionId) == 0) return;
            MissionRow mission = missions.findById(row.getMissionId(), row.getUserId());
            String topic = topic(mission == null ? null : mission.getParams(), row.getTemplate());
            if (topic == null) {
                reviews.complete(submissionId, "SKIPPED", null, null, null, null, 0);
                return;
            }
            String filename = row.getMediaUrl().substring(row.getMediaUrl().lastIndexOf('/') + 1);
            for (int attempt = 1; attempt <= 2; attempt++) {
                TopicFitResult result;
                try {
                    byte[] image = storage.load(row.getMediaUrl());
                    result = client.call(row.getTemplate(), topic, image, filename);
                } catch (RuntimeException e) {
                    log.warn("Vision attempt {} failed for submission {} ({})", attempt, submissionId, e.getClass().getSimpleName());
                    if (attempt == 2) {
                        reviews.complete(submissionId, "FAILED", null, null, null, null, attempt);
                        return;
                    } else try {
                        Thread.sleep(properties.retryDelay().toMillis());
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        reviews.complete(submissionId, "FAILED", null, null, null, null, attempt);
                        return;
                    }
                    continue;
                }
                TopicFitJudge.Verdict verdict = judge.judge(result.fit(), properties.thresholdFor(row.getTemplate()), submissionId);
                reviews.complete(submissionId, "DONE", result.fit(), verdict.badge(), verdict.comment(),
                        result.model() + "/" + result.version(), attempt);
                try { mascot.commentPhotoScore(submissionId, verdict.comment(),
                        judge.score(result.fit(), properties.thresholdFor(row.getTemplate()))); }
                catch (RuntimeException error) { log.warn("Mascot score comment failed for submission {}", submissionId, error); }
                return;
            }
        } catch (RuntimeException e) {
            log.error("Vision review failed for submission {}", submissionId, e);
        }
    }

    private String topic(String params, String template) {
        if (params == null) return null;
        try {
            Map<?, ?> values = json.readValue(params, Map.class);
            Object value = values.get("PHOTO_NOW".equals(template) ? "subject" : "color");
            if (!(value instanceof String text)) return null;
            text = text.strip();
            return text.length() >= 2 && text.length() <= 60 ? text : null;
        } catch (Exception e) {
            return null;
        }
    }
}
