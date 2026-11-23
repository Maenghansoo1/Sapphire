package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.config.BanditProperties;
import com.sapphire.hawe.mission.domain.BanditReward;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 밴딧 반영 잡(TODO_2/D3)의 흐름 — 통계 서비스는 가짜로. 감쇠 SQL 은 BanditStatsIntegrationTests 에서 실제 DB 로. */
class BanditApplyJobTests {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 24, 3, 0);
    private final BanditStatsService stats = mock(BanditStatsService.class);

    private static BanditProperties settings(boolean enabled, int batchSize) {
        return new BanditProperties(null, null, null, null, null, null, null, null, null, null, null, enabled, batchSize, 20, null);
    }

    private static BanditReward reward(long id) {
        return new BanditReward(id, 1, "PHOTO_NOW", 3, 1.5);
    }

    @Test void disabledDoesNothing() {
        BanditApplyJob job = new BanditApplyJob(stats, settings(false, 500));
        assertEquals(BanditApplyJob.Result.DISABLED, job.applyOnce(NOW));
        job.run();
        verifyNoInteractions(stats);
    }

    @Test void decaysFirstThenAppliesAndKeepsGoingPastAFailure() {
        when(stats.decay(NOW)).thenReturn(7);
        when(stats.pendingRewards(NOW, 500)).thenReturn(List.of(reward(1), reward(2), reward(3), reward(4)));
        when(stats.apply(any(), eq(NOW))).thenReturn(true);
        when(stats.apply(eq(reward(2)), eq(NOW))).thenThrow(new IllegalStateException("db"));
        when(stats.apply(eq(reward(3)), eq(NOW))).thenReturn(false);  // 다른 실행이 먼저 반영함

        BanditApplyJob.Result result = new BanditApplyJob(stats, settings(true, 500)).applyOnce(NOW);
        assertEquals(new BanditApplyJob.Result(7, 2, 1, 1), result);
        InOrder order = inOrder(stats);
        order.verify(stats).decay(NOW);  // 보상을 더하기 전에 감쇠
        order.verify(stats).pendingRewards(NOW, 500);
        verify(stats).apply(reward(4), NOW);  // 실패한 2번 뒤에도 계속
    }

    @Test void largeBacklogIsProcessedInBatches() {
        when(stats.pendingRewards(NOW, 2)).thenReturn(List.of(reward(1), reward(2)), List.of(reward(3), reward(4)),
                List.of(reward(5)));
        when(stats.apply(any(), eq(NOW))).thenReturn(true);
        BanditApplyJob.Result result = new BanditApplyJob(stats, settings(true, 2)).applyOnce(NOW);
        assertEquals(5, result.applied());
        verify(stats, times(3)).pendingRewards(NOW, 2);  // 마지막 묶음이 덜 차면 끝
    }

    @Test void stopsWhenABatchMakesNoProgress() {
        when(stats.pendingRewards(NOW, 1)).thenReturn(List.of(reward(9)));
        when(stats.apply(any(), eq(NOW))).thenThrow(new IllegalStateException("db"));
        BanditApplyJob.Result result = new BanditApplyJob(stats, settings(true, 1)).applyOnce(NOW);
        assertEquals(1, result.failed());
        verify(stats, times(1)).pendingRewards(NOW, 1);  // 같은 실패를 계속 붙잡고 돌지 않아요
    }

    @Test void scheduledRunSwallowsErrors() {
        when(stats.decay(any())).thenThrow(new IllegalStateException("db down"));
        assertDoesNotThrow(() -> new BanditApplyJob(stats, settings(true, 500)).run());
    }
}
