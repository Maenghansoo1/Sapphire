package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.config.BanditProperties;
import com.sapphire.hawe.global.time.SeoulTime;
import com.sapphire.hawe.mission.domain.BanditReward;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 밴딧 통계 반영 잡(TODO_2/D3). 새벽 한 번(기본 03:00 서울) ① 모든 통계에 시간 감쇠 ② 새로 닫힌 미션의 보상을 반영한다.
 * 하루에 한 번만 바꾸므로 같은 날 여러 슬롯은 같은 통계를 읽는다(D4 의 슬롯 간 일관성).
 * {@code hawe.bandit.enabled=false}(기본)면 아무것도 하지 않는다. 선택 정책(D4)과 따로 켜서 통계를 미리 쌓을 수 있다.
 * 미션 한 건이 실패해도 나머지는 반영하고, 로그에는 미션 번호와 사유만 남긴다.
 */
@Component
public class BanditApplyJob {
    private static final Logger log = LoggerFactory.getLogger(BanditApplyJob.class);

    private final BanditStatsService stats;
    private final BanditProperties properties;

    public BanditApplyJob(BanditStatsService stats, BanditProperties properties) {
        this.stats = stats;
        this.properties = properties;
    }

    /** 실행 결과: 감쇠한 통계 행 수, 반영한 미션 수, 이미 반영돼 건너뛴 수, 실패한 수. */
    public record Result(int decayed, int applied, int skipped, int failed) {
        static final Result DISABLED = new Result(0, 0, 0, 0);
    }

    @Scheduled(cron = "${hawe.bandit.apply-cron:0 0 3 * * *}", zone = "Asia/Seoul")
    public void run() {
        if (!properties.enabled()) return;
        try {
            Result result = applyOnce(SeoulTime.now());
            log.info("Bandit stats applied: decayed={} applied={} skipped={} failed={}",
                    result.decayed(), result.applied(), result.skipped(), result.failed());
        } catch (RuntimeException e) {
            log.error("Bandit apply job failed", e);
        }
    }

    /**
     * 한 번 실행(스케줄러·테스트·운영 확인용, 컨트롤러로는 열지 않는다). 감쇠 → 닫힌 미션을 {@code batch-size} 개씩,
     * 최대 {@code max-batches-per-run} 묶음까지 반영한다. 한 묶음에서 하나도 반영하지 못하면(모두 실패) 멈춘다 — 다음 실행에 다시 시도.
     */
    public Result applyOnce(LocalDateTime now) {
        if (!properties.enabled()) return Result.DISABLED;
        int decayed = stats.decay(now);
        int applied = 0;
        int skipped = 0;
        int failed = 0;
        for (int batch = 0; batch < properties.maxBatchesPerRun(); batch++) {
            List<BanditReward> rewards = stats.pendingRewards(now, properties.batchSize());
            int progressed = 0;
            for (BanditReward reward : rewards) {
                try {
                    if (stats.apply(reward, now)) applied++;
                    else skipped++;
                    progressed++;
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("Bandit apply failed for mission {} ({})", reward.missionId(), e.getClass().getSimpleName());
                }
            }
            if (rewards.size() < properties.batchSize() || progressed == 0) break;
        }
        return new Result(decayed, applied, skipped, failed);
    }
}
