package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.mission.dao.ExperimentMissionRow;
import com.sapphire.hawe.mission.domain.ExperimentStats.Interval;
import com.sapphire.hawe.mission.dto.ExperimentReport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentReportServiceTests {

    private static final LocalDate DAY1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate DAY2 = LocalDate.of(2026, 10, 2);

    /*
     * 그룹 1: 1일 AI(슬롯 2개: 3/4, 4/4), 2일 랜덤(2/4)
     * 그룹 2: 1일 랜덤(1/2), 2일 AI(2/2, AI 실패로 고정 문장)
     * 그룹 3: 1일 AI(3/3)만 — 대응 비교에서 빠짐
     */
    private static final List<ExperimentMissionRow> ROWS = List.of(
            row(1, DAY1, "AI", "AI", 4, 3, 2, 5, 600L),
            row(1, DAY1, "AI", "AI", 4, 4, 4, 3, 120L),
            row(2, DAY2, "AI", "AI_FALLBACK", 2, 2, 2, 0, 300L),
            row(3, DAY1, "AI", "AI", 3, 3, 3, 4, 60L),
            row(1, DAY2, "RANDOM", "PLACEHOLDER", 4, 2, 1, 2, 1800L),
            row(2, DAY1, "RANDOM", "PLACEHOLDER", 2, 1, 1, 0, 900L),
            row(4, DAY1, null, "AI", 5, 5, 5, 0, 10L));          // 실험 밖 미션은 세지 않는다

    @Test
    void armSummariesFollowTheDefinitions() {
        ExperimentReport report = ExperimentReportService.build(ROWS, DAY1, DAY2);
        ExperimentReport.ArmSummary ai = report.ai();
        assertEquals(4, ai.missions());
        assertEquals(3, ai.groups());
        assertEquals(3, ai.groupDays());
        assertEquals(13, ai.eligible());
        assertEquals(12, ai.submitted());
        assertEquals(0.9231, ai.participation());                  // 12 / 13
        assertTrue(ai.participationCi95().low() < 0.9231 && 0.9231 < ai.participationCi95().high());
        assertEquals(0.9583, ai.groupDayMeanParticipation());      // (7/8 + 1 + 1) / 3
        assertEquals(0.9167, ai.onTimeRate());                     // 11 / 12
        assertEquals(3.0, ai.engagementPerMission());              // 12 / 4
        assertEquals(3.5, ai.medianFirstSubmitMinutes());          // 1, 2, 5, 10분
        ExperimentReport.ArmSummary random = report.random();
        assertEquals(2, random.missions());
        assertEquals(0.5, random.participation());
        assertEquals(new Interval(0.1876, 0.8124), random.participationCi95());
        assertEquals(0.6667, random.onTimeRate());
        assertEquals(22.5, random.medianFirstSubmitMinutes());
    }

    @Test
    void comparisonsAndPairedDifference() {
        ExperimentReport report = ExperimentReportService.build(ROWS, DAY1, DAY2);
        assertEquals(42.31, report.difference().differencePoints());   // (12/13 − 3/6) × 100
        assertEquals(2.103, report.difference().z());
        assertEquals(0.0355, report.difference().pValue(), 0.0005);
        // AI 가 실제로 만든 미션만: 10 / 11
        assertEquals(1, report.aiFallbackMissions());
        assertEquals(0.9091, report.aiPerProtocol().participation());
        assertEquals(40.91, report.perProtocolDifference().differencePoints());

        ExperimentReport.Paired paired = report.paired();
        assertEquals(2, paired.groups());                  // 그룹 1: 87.5 − 50 = 37.5, 그룹 2: 100 − 50 = 50
        assertEquals(1, paired.excludedGroups());
        assertEquals(43.75, paired.meanDifferencePoints());
        assertEquals(new Interval(-35.66, 123.16), paired.ci95());   // 43.75 ± 12.706 × 6.25
        assertEquals(new Interval(37.5, 50.0), paired.bootstrapCi95());
        assertEquals(2, paired.positive());
        assertEquals(0, paired.negative());
        assertEquals(0.5, paired.signTestP());
        assertTrue(report.underpowered());
        assertTrue(report.warnings().stream().anyMatch(w -> w.startsWith("AI 군 미션 1개는")));
        assertTrue(report.warnings().stream().anyMatch(w -> w.startsWith("한쪽 군만 겪은 그룹 1개")));
        assertEquals(report, ExperimentReportService.build(ROWS, DAY1, DAY2));   // 같은 입력 → 같은 결과
    }

    @Test
    void emptyPeriodHasNoNumbersButSaysWhy() {
        ExperimentReport report = ExperimentReportService.build(List.of(), DAY1, DAY2);
        assertNull(report.ai().participation());
        assertNull(report.difference().differencePoints());
        assertNull(report.paired().meanDifferencePoints());
        assertNull(report.paired().signTestP());
        assertTrue(report.underpowered());
        assertTrue(report.warnings().getFirst().startsWith("기간 안에 닫힌 실험 미션이 없다"));
        assertTrue(ExperimentReportService.markdown(report).contains("AI - vs 랜덤 -"));
    }

    @Test
    void reportsCarryNoIdentifiers() {
        ExperimentReport report = ExperimentReportService.build(ROWS, DAY1, DAY2);
        String json = new ObjectMapper().writeValueAsString(report);
        for (String key : List.of("groupId", "missionId", "userId", "nickname"))
            assertFalse(json.contains(key), key);
    }

    @Test
    void markdownIsReadyToPaste() {
        String markdown = ExperimentReportService.markdown(ExperimentReportService.build(ROWS, DAY1, DAY2));
        assertTrue(markdown.startsWith("# AI 주제 vs 랜덤 주제 — 참여율 비교\n"));
        assertTrue(markdown.contains("기간: 2026-10-01 ~ 2026-10-02"));
        assertTrue(markdown.contains("**주의: 표본이 작아 결과를 믿기 어렵다.**"));
        assertTrue(markdown.contains("AI 92.3% vs 랜덤 50.0%, 차이 +42.3%p"));
        assertTrue(markdown.contains("그룹 2개, 평균 차이 +43.8%p (95% CI -35.7 ~ +123.2%p, 부트스트랩 +37.5 ~ +50.0%p, 부호 검정 p 0.500)"));
        assertTrue(markdown.contains("| 참여율 (95% CI) | 92.3% ("));
        assertTrue(markdown.contains("| 50.0% (18.8~81.2%) |"));
        assertTrue(markdown.contains("| 첫 제출까지 중앙값(분) | 3.5 | 22.5 |"));
        assertTrue(markdown.contains("미션 1개를 빼고: AI 90.9% vs 랜덤 50.0%, 차이 +40.9%p"));
    }

    private static ExperimentMissionRow row(long groupId, LocalDate date, String arm, String source, long eligible,
            long submitted, long onTime, long engagement, Long firstSubmitSeconds) {
        ExperimentMissionRow row = new ExperimentMissionRow();
        row.setGroupId(groupId);
        row.setMissionDate(date);
        row.setExperimentArm(arm);
        row.setGenerationSource(source);
        row.setEligible(eligible);
        row.setSubmitted(submitted);
        row.setOnTime(onTime);
        row.setEngagement(engagement);
        row.setFirstSubmitSeconds(firstSubmitSeconds);
        return row;
    }
}
