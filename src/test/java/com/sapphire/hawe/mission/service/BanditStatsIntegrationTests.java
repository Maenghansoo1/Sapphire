package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.group.service.GroupService;
import com.sapphire.hawe.mission.domain.BanditArmStats;
import com.sapphire.hawe.mission.domain.BanditReward;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 밴딧 통계(TODO_2/D2)를 실제 DB 로. 미션 날짜는 미래로 잡아서 매분 도는 발행 스케줄러가 오늘 미션을 넣어도
 * 이 테스트 미션의 "현재/닫힘"이 바뀌지 않게 했다(마감만 과거로 둔다).
 */
@SpringBootTest
class BanditStatsIntegrationTests {
    private static final String TEMPLATE = "TEXT_ACROSTIC";
    @Autowired BanditStatsService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired GroupService groups;

    @Test void closedMissionRewardIsAppliedOnceToGlobalAndGroupStats() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        long me = user(suffix + "a");
        long friend = user(suffix + "b");
        String code = suffix.substring(0, 6).toUpperCase();
        jdbc.update("INSERT INTO `groups`(name,owner_id,invite_code) VALUES ('Bandit test',?,?)", me, code);
        long group = jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code=?", Long.class, code);
        jdbc.update("INSERT INTO group_members(group_id,user_id,role) VALUES (?,?,'owner')", group, me);
        jdbc.update("INSERT INTO group_members(group_id,user_id,role) VALUES (?,?,'member')", group, friend);
        LocalDate base = LocalDate.now().plusDays(40);
        LocalDateTime now = LocalDateTime.now();
        BanditArmStats globalBefore = service.snapshot(group).global(TEMPLATE);
        long closed = 0;
        try {
            closed = mission(group, base, now.minusHours(1));
            long current = mission(group, base.plusDays(2), now.minusHours(1));  // 마감은 지났지만 아직 현재 미션(늦은 제출 가능)
            long open = mission(group, base.plusDays(1), now.plusDays(1));  // 마감 전
            // 나: 공백 뺀 15자 → effort 15/30 = 0.5, 친구의 반응 1개 → 평균 1 → 1/(1+1) = 0.5 (2명 그룹이라 ÷2 안 함)
            jdbc.update("INSERT INTO submissions(mission_id,user_id,answer_data,submitted_at) VALUES (?,?,?,NOW())",
                    closed, me, "{\"text\":\"가나다라마 바사아자차\\n카타파하거\"}");
            long submission = jdbc.queryForObject("SELECT id FROM submissions WHERE mission_id=?", Long.class, closed);
            jdbc.update("INSERT INTO reactions(submission_id,user_id,sticker_type) VALUES (?,?,'heart')", submission, friend);
            Long mascot = jdbc.queryForObject("SELECT MIN(id) FROM users WHERE is_ai = 1", Long.class);
            if (mascot != null)  // AI 마스코트 반응은 세지 않아요
                jdbc.update("INSERT INTO reactions(submission_id,user_id,sticker_type) VALUES (?,?,'fire')", submission, mascot);

            List<BanditReward> pending = mine(service.pendingRewards(now, 100_000), closed, current, open);
            assertEquals(1, pending.size(), "닫힌 미션만 나와요(현재 미션·마감 전 미션 제외)");
            BanditReward reward = pending.getFirst();
            assertEquals(closed, reward.missionId());
            assertEquals(2, reward.eligible());  // 친구는 안 냈지만 대상
            assertEquals(0.6 + 0.2 * 0.5 + 0.2 * 0.5, reward.total(), 1e-9);  // 나 0.8, 친구 0

            assertTrue(service.apply(reward, now));
            assertFalse(service.apply(reward, now), "같은 미션은 한 번만 반영돼요");
            BanditStatsService.Snapshot snapshot = service.snapshot(group);
            assertEquals(new BanditArmStats(0.8, 1.2, 0, 0, 1), round(snapshot.group(TEMPLATE)));
            BanditArmStats globalAfter = snapshot.global(TEMPLATE);
            assertEquals(0.8, globalAfter.alphaReal() - globalBefore.alphaReal(), 1e-9);
            assertEquals(1.2, globalAfter.betaReal() - globalBefore.betaReal(), 1e-9);
            assertEquals(1, globalAfter.missionCount() - globalBefore.missionCount());
            assertEquals(BanditArmStats.EMPTY, snapshot.group("PHOTO_NOW"));  // 반영된 적 없는 틀
            assertTrue(mine(service.pendingRewards(now, 100_000), closed, current, open).isEmpty(), "반영한 미션은 다시 안 나와요");
        } finally {
            // 그룹을 지우면 반영 기록·그룹 통계가 미션보다 먼저 지워져 FK 오류가 없어야 해요
            groups.leaveGroup(friend, group, null);
            groups.leaveGroup(me, group, null);
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM template_bandit_stats WHERE scope='GROUP' AND group_id=?",
                    Integer.class, group));
            if (closed != 0) assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM template_bandit_applied WHERE mission_id=?", Integer.class, closed));
            // 전역 통계는 남는 게 정상이지만, 테스트가 더한 몫은 되돌려 로컬 DB 를 깨끗하게 둔다
            jdbc.update("UPDATE template_bandit_stats SET alpha_real=?, beta_real=?, n_missions=? "
                    + "WHERE scope='GLOBAL' AND group_id=0 AND template=?", globalBefore.alphaReal(), globalBefore.betaReal(),
                    globalBefore.missionCount(), TEMPLATE);
            jdbc.update("DELETE FROM users WHERE id IN (?,?)", me, friend);
        }
    }

    private static List<BanditReward> mine(List<BanditReward> rewards, long... ids) {
        return rewards.stream().filter(r -> java.util.Arrays.stream(ids).anyMatch(id -> id == r.missionId())).toList();
    }

    private static BanditArmStats round(BanditArmStats s) {
        return new BanditArmStats(Math.round(s.alphaReal() * 1e6) / 1e6, Math.round(s.betaReal() * 1e6) / 1e6,
                s.alphaSyn(), s.betaSyn(), s.missionCount());
    }

    private long user(String name) {
        String email = name + "@test.com";
        jdbc.update("INSERT INTO users(email,password_hash,nickname,terms_agreed_at,privacy_agreed_at) VALUES (?,'x','Bandit',NOW(),NOW())",
                email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
    }

    private long mission(long group, LocalDate day, LocalDateTime deadline) {
        jdbc.update("INSERT INTO missions(group_id,content,category,mission_date,template,params,slot_no,published_at,deadline_at) "
                + "VALUES (?,'삼행시',?,?,?,'{}',1,?,?)", group, "TEST", day, TEMPLATE, day.atTime(9, 0), deadline);
        return jdbc.queryForObject("SELECT id FROM missions WHERE group_id=? AND mission_date=?", Long.class, group, day);
    }
}
