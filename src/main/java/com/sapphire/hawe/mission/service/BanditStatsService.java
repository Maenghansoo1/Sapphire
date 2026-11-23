package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.config.BanditProperties;
import com.sapphire.hawe.global.config.VisionProperties;
import com.sapphire.hawe.mission.dao.BanditMapper;
import com.sapphire.hawe.mission.dao.BanditMissionRow;
import com.sapphire.hawe.mission.dao.BanditStatsRow;
import com.sapphire.hawe.mission.dao.BanditSubmissionRow;
import com.sapphire.hawe.mission.domain.BanditArmStats;
import com.sapphire.hawe.mission.domain.BanditReward;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미션 틀 선택 밴딧의 보상 계산·통계 저장(TODO_2/D2). 닫힌 미션만 반영하고, 같은 미션은 한 번만 반영한다.
 * 이 단계에서는 선택에 쓰지 않는다 — 주기 반영 잡은 D3, 선택기 연결은 D4.
 * 합성 prior(alpha_syn/beta_syn)는 D1 시뮬레이션에서 이득이 없어 채우지 않는다(컬럼만 0 으로 둔다).
 */
@Service
@EnableConfigurationProperties(BanditProperties.class)
public class BanditStatsService {
    public static final String GLOBAL = "GLOBAL";
    public static final String GROUP = "GROUP";
    /** 전역 행의 group_id(UNIQUE 가 NULL 을 여러 개 허용해서 0 으로 둔다) */
    public static final long GLOBAL_GROUP_ID = 0L;

    private final BanditMapper mapper;
    private final BanditRewardCalculator calculator;

    public BanditStatsService(BanditMapper mapper, BanditProperties properties, VisionProperties vision) {
        this.mapper = mapper;
        this.calculator = new BanditRewardCalculator(properties, vision::thresholdFor);
    }

    /** 아직 반영하지 않은 닫힌 미션의 보상(오래된 것부터 [limit] 건). */
    @Transactional(readOnly = true)
    public List<BanditReward> pendingRewards(LocalDateTime now, int limit) {
        List<BanditMissionRow> missions = mapper.findUnappliedClosed(now, limit);
        if (missions.isEmpty()) return List.of();
        Map<Long, List<BanditRewardCalculator.Submission>> submissions = mapper.findSubmissions(
                missions.stream().map(BanditMissionRow::getMissionId).toList()).stream()
                .collect(Collectors.groupingBy(BanditSubmissionRow::getMissionId, Collectors.mapping(row ->
                        new BanditRewardCalculator.Submission(row.getTextLength(), row.isHasCaption(), row.getFitScore(),
                                row.getEngagement()), Collectors.toList())));
        return missions.stream().map(row -> calculator.reward(row.getMissionId(), row.getGroupId(), row.getTemplate(),
                row.getEligible(), submissions.getOrDefault(row.getMissionId(), List.of()))).toList();
    }

    /**
     * 보상 하나를 전역·그룹 통계에 함께 더한다(α += R, β += n − R). 이미 반영된 미션이면 아무것도 안 하고 false(동시 실행에도 안전).
     * 대상 인원이 0 이면 반영 기록만 남긴다.
     */
    @Transactional
    public boolean apply(BanditReward reward, LocalDateTime now) {
        try {
            mapper.insertApplied(reward.missionId(), now);
        } catch (DuplicateKeyException alreadyApplied) {
            return false;
        }
        if (reward.eligible() <= 0 || reward.template() == null) return true;
        mapper.addStats(GLOBAL, GLOBAL_GROUP_ID, reward.template(), reward.alpha(), reward.beta(), now);
        mapper.addStats(GROUP, reward.groupId(), reward.template(), reward.alpha(), reward.beta(), now);
        return true;
    }

    /** 선택기(D4)가 쓸 통계: 틀 → 전역 / 틀 → 그 그룹. 행이 없는 틀은 빠져 있다({@link BanditArmStats#EMPTY} 로 다룬다). */
    @Transactional(readOnly = true)
    public Snapshot snapshot(long groupId) {
        Map<String, BanditArmStats> global = new LinkedHashMap<>();
        Map<String, BanditArmStats> group = new LinkedHashMap<>();
        for (BanditStatsRow row : mapper.findStats(groupId)) {
            BanditArmStats stats = new BanditArmStats(row.getAlphaReal(), row.getBetaReal(), row.getAlphaSyn(),
                    row.getBetaSyn(), row.getMissionCount());
            (GLOBAL.equals(row.getScope()) ? global : group).put(row.getTemplate(), stats);
        }
        return new Snapshot(Map.copyOf(global), Map.copyOf(group));
    }

    public record Snapshot(Map<String, BanditArmStats> global, Map<String, BanditArmStats> group) {
        public BanditArmStats global(String template) { return global.getOrDefault(template, BanditArmStats.EMPTY); }
        public BanditArmStats group(String template) { return group.getOrDefault(template, BanditArmStats.EMPTY); }
    }
}
