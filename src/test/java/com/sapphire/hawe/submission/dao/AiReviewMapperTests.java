package com.sapphire.hawe.submission.dao;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class AiReviewMapperTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired AiReviewMapper reviews;

    @Test void claimIsUniqueMissingForeignKeyIsIgnoredAndOnlyDoneRowsAreReturned() {
        Long[] ids = submissions(4);
        assertEquals(1, reviews.insertPendingIfAbsent(ids[0]));
        assertEquals(0, reviews.insertPendingIfAbsent(ids[0]));
        assertEquals(0, reviews.insertPendingIfAbsent(Long.MAX_VALUE));
        assertEquals("PENDING", reviews.findBySubmissionId(ids[0]).getStatus());

        assertEquals(1, reviews.complete(ids[0], "DONE", 0.5, "PERFECT_FIT", "좋아요", "model/1", 1));
        assertEquals(1, reviews.insertPendingIfAbsent(ids[1]));
        assertEquals(1, reviews.complete(ids[1], "FAILED", null, null, null, null, 2));
        assertEquals(1, reviews.insertPendingIfAbsent(ids[2]));
        assertEquals(1, reviews.complete(ids[2], "SKIPPED", null, null, null, null, 0));
        assertEquals(1, reviews.insertPendingIfAbsent(ids[3]));

        assertEquals(ids[0], reviews.findBySubmissionId(ids[0]).getSubmissionId());
        assertEquals("PERFECT_FIT", reviews.findBySubmissionId(ids[0]).getBadge());
        assertEquals(0.5, reviews.findBySubmissionId(ids[0]).getFitScore());
        assertEquals("DONE", reviews.findBySubmissionId(ids[0]).getStatus());
        assertEquals("FAILED", reviews.findBySubmissionId(ids[1]).getStatus());
        assertEquals("SKIPPED", reviews.findBySubmissionId(ids[2]).getStatus());
        assertEquals("PENDING", reviews.findBySubmissionId(ids[3]).getStatus());
    }

    private Long[] submissions(int count) {
        String email = UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (nickname,email,password_hash,terms_agreed_at,privacy_agreed_at) "
                + "VALUES ('Writer',?,'unused',NOW(),NOW())", email);
        Long user = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        String invite = UUID.randomUUID().toString().substring(0, 6);
        jdbc.update("INSERT INTO `groups` (name,owner_id,invite_code) VALUES ('Review group',?,?)", user, invite);
        Long group = jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code=?", Long.class, invite);
        Long[] ids = new Long[count];
        for (int i = 0; i < count; i++) {
            jdbc.update("INSERT INTO missions (group_id,content,category,mission_date,template,params,slot_no,published_at,deadline_at) "
                    + "VALUES (?,'Photo topic','DAILY',DATE_ADD(CURDATE(), INTERVAL ? DAY),'PHOTO_NOW','{}',1,NOW(),DATE_ADD(NOW(), INTERVAL 1 DAY))",
                    group, i);
            Long mission = jdbc.queryForObject("SELECT id FROM missions WHERE group_id=? AND mission_date=DATE_ADD(CURDATE(), INTERVAL ? DAY)",
                    Long.class, group, i);
            jdbc.update("INSERT INTO submissions (mission_id,user_id) VALUES (?,?)", mission, user);
            ids[i] = jdbc.queryForObject("SELECT id FROM submissions WHERE mission_id=?", Long.class, mission);
        }
        return ids;
    }
}
