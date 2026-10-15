package com.sapphire.hawe.insight.service;

import com.sapphire.hawe.global.config.InsightProperties;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import com.sapphire.hawe.global.storage.FileStorage;
import com.sapphire.hawe.global.time.SeoulTime;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.service.GroupErrorCode;
import com.sapphire.hawe.insight.dao.InsightMapper;
import com.sapphire.hawe.insight.dao.InsightRow;
import com.sapphire.hawe.insight.domain.InsightPayload;
import com.sapphire.hawe.insight.dto.GroupInsightResponse;
import com.sapphire.hawe.insight.dto.InsightGeneratingResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class GroupInsightService {
    private static final Logger log = LoggerFactory.getLogger(GroupInsightService.class);
    private final GroupMapper groups;
    private final GroupMemberMapper members;
    private final InsightMapper mapper;
    private final InsightGenerator generator;
    private final InsightProperties properties;
    private final FileStorage storage;
    private final ObjectMapper json;
    private final HighlightComposer highlights = new HighlightComposer();

    public GroupInsightService(GroupMapper groups, GroupMemberMapper members, InsightMapper mapper,
            InsightGenerator generator, InsightProperties properties, FileStorage storage, ObjectMapper json) {
        this.groups = groups;
        this.members = members;
        this.mapper = mapper;
        this.generator = generator;
        this.properties = properties;
        this.storage = storage;
        this.json = json;
    }

    public ResponseEntity<?> get(Long userId, Long groupId, YearMonth requestedMonth) {
        if (groups.findById(groupId) == null) throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        if (!members.existsByGroupIdAndUserId(groupId, userId))
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        YearMonth current = YearMonth.now(SeoulTime.ZONE);
        YearMonth month = requestedMonth == null ? current : requestedMonth;
        if (month.isAfter(current) || month.isBefore(current.minusMonths(12)))
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);

        InsightRow cache = mapper.findCache(groupId, month.toString());
        if (cache != null && "READY".equals(cache.getStatus()) && !cache.isExpired())
            return ResponseEntity.ok(ready(groupId, month, cache, false));
        if (cache != null && "READY".equals(cache.getStatus()) && cache.isExpired()) {
            start(groupId, month, cache);
            return ResponseEntity.ok(ready(groupId, month, cache, true));
        }
        if (cache != null && "FAILED".equals(cache.getStatus()) && cache.getPayload() != null) {
            start(groupId, month, cache);
            return ResponseEntity.ok(ready(groupId, month, cache, true));
        }
        if (cache != null && "GENERATING".equals(cache.getStatus()) && cache.getPayload() != null) {
            if (cache.isAbandoned()) start(groupId, month, cache);
            return ResponseEntity.ok(ready(groupId, month, cache, true));
        }
        if (cache == null || "FAILED".equals(cache.getStatus())
                || ("GENERATING".equals(cache.getStatus()) && cache.isAbandoned())) start(groupId, month, cache);
        return ResponseEntity.accepted().body(new InsightGeneratingResponse(groupId, month.toString()));
    }

    public void generatePreviousMonth(LocalDate today) {
        if (!properties.enabled()) return;
        YearMonth month = YearMonth.from(today).minusMonths(1);
        for (Long groupId : mapper.findGroupIdsWithMissions(month.atDay(1), month.atEndOfMonth())) {
            try {
                InsightRow cache = mapper.findCache(groupId, month.toString());
                if (cache == null || !"READY".equals(cache.getStatus())) start(groupId, month, cache);
            } catch (RuntimeException e) {
                log.error("Insight month-end generation failed for group {}", groupId, e);
            }
        }
    }

    private void start(Long groupId, YearMonth month, InsightRow cache) {
        int claimed = cache == null ? mapper.tryStart(groupId, month.toString()) : mapper.restart(groupId, month.toString());
        if (claimed == 1) generator.generate(groupId, month);
    }

    private GroupInsightResponse ready(Long groupId, YearMonth month, InsightRow cache, boolean stale) {
        InsightPayload payload = json.readValue(cache.getPayload(), InsightPayload.class);
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (payload.fastest() != null) ids.add(payload.fastest().userId());
        if (payload.mostReacted() != null) ids.add(payload.mostReacted().authorUserId());
        Map<Long, InsightRow> profiles = ids.isEmpty() ? Map.of() : mapper.findDisplayProfiles(groupId, List.copyOf(ids))
                .stream().collect(Collectors.toMap(InsightRow::getUserId, Function.identity()));
        GroupInsightResponse.ActivityIndex activity = null;
        if (payload.activityIndex() != null) {
            Integer delta = null;
            InsightRow previous = mapper.findCache(groupId, month.minusMonths(1).toString());
            if (previous != null && "READY".equals(previous.getStatus()) && previous.getPayload() != null) {
                InsightPayload prior = json.readValue(previous.getPayload(), InsightPayload.class);
                if (prior.activityIndex() != null) delta = payload.activityIndex().score() - prior.activityIndex().score();
            }
            var source = payload.activityIndex();
            activity = new GroupInsightResponse.ActivityIndex(source.score(), delta,
                    source.participationRate(), source.onTimeRate(), source.interactionRate());
        }
        GroupInsightResponse.Fastest fastest = null;
        if (payload.fastest() != null) {
            var source = payload.fastest();
            InsightRow profile = profiles.get(source.userId());
            fastest = new GroupInsightResponse.Fastest(source.userId(), profile == null ? null : profile.getNickname(),
                    profile == null ? null : profile.getCharacterKey(), profile == null || profile.getProfileImageUrl() == null
                            ? null : storage.publicUrl(profile.getProfileImageUrl()), source.firstCount(),
                    source.averageResponseSeconds(), source.fastestSingleSeconds());
        }
        GroupInsightResponse.MostReacted most = null;
        if (payload.mostReacted() != null) {
            var source = payload.mostReacted();
            InsightRow profile = profiles.get(source.authorUserId());
            most = new GroupInsightResponse.MostReacted(source.submissionId(), source.missionId(),
                    source.missionContent(), source.template(), source.authorUserId(),
                    profile == null ? null : profile.getNickname(), source.reactionCount(),
                    source.commentCount(), source.topSticker());
        }
        return new GroupInsightResponse(groupId, month.toString(), "READY", stale, payload.empty(),
                SeoulTime.toOffset(cache.getGeneratedAt()), activity, fastest, payload.topWords(), most,
                highlights.compose(month, activity, fastest, payload.topWords(), most));
    }
}
