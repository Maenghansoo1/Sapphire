package com.sapphire.hawe.insight.service;

import com.sapphire.hawe.global.config.InsightProperties;
import com.sapphire.hawe.insight.dao.InsightMapper;
import com.sapphire.hawe.insight.dao.InsightRow;
import com.sapphire.hawe.insight.domain.InsightPayload;
import java.time.YearMonth;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@EnableConfigurationProperties(InsightProperties.class)
public class InsightGenerator {
    private static final Logger log = LoggerFactory.getLogger(InsightGenerator.class);
    private static final int FINALIZATION_GRACE_DAYS = 2;
    private final InsightMapper mapper;
    private final InsightProperties properties;
    private final KeywordExtractor keywords;
    private final ObjectMapper json;

    public InsightGenerator(InsightMapper mapper, InsightProperties properties, KeywordExtractor keywords, ObjectMapper json) {
        this.mapper = mapper;
        this.properties = properties;
        this.keywords = keywords;
        this.json = json;
    }

    @Async("insightExecutor")
    public void generate(Long groupId, YearMonth month) {
        try {
            List<InsightRow> missions = mapper.findClosedMissions(groupId, month.atDay(1), month.atEndOfMonth());
            List<InsightRow> submissions = List.of();
            List<InsightRow> reactions = List.of(), comments = List.of(), stickers = List.of();
            if (!missions.isEmpty()) {
                submissions = mapper.findSubmissions(missions.stream().map(InsightRow::getId).toList());
                if (!submissions.isEmpty()) {
                    List<Long> ids = submissions.stream().map(InsightRow::getId).toList();
                    reactions = mapper.countReactionsBySubmission(ids);
                    comments = mapper.countCommentsBySubmission(ids);
                    stickers = mapper.countStickersBySubmission(ids);
                }
            }
            InsightPayload payload = new InsightCalculator(keywords, properties.topWords(), properties.minWordCount())
                    .calculate(missions, submissions, reactions, comments, stickers);
            boolean hasCurrentMission = mapper.hasCurrentMissionInMonth(groupId, month.atDay(1), month.atEndOfMonth());
            mapper.complete(groupId, month.toString(), json.writeValueAsString(payload),
                    month.plusMonths(1).atDay(1).plusDays(FINALIZATION_GRACE_DAYS).atStartOfDay(),
                    hasCurrentMission,
                    properties.currentMonthTtl().toSeconds());
        } catch (RuntimeException e) {
            try {
                if (mapper.fail(groupId, month.toString()) == 0) return; // Group or cache was removed.
            } catch (RuntimeException updateError) {
                log.error("Could not mark insight generation failed for group {} month {}", groupId, month, updateError);
            }
            log.error("Insight generation failed for group {} month {}", groupId, month, e);
        }
    }
}
