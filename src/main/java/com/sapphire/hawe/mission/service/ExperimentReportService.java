package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.time.SeoulTime;
import com.sapphire.hawe.mission.dao.ExperimentMapper;
import com.sapphire.hawe.mission.dao.ExperimentMissionRow;
import com.sapphire.hawe.mission.domain.ExperimentArm;
import com.sapphire.hawe.mission.domain.ExperimentStats;
import com.sapphire.hawe.mission.domain.ExperimentStats.Interval;
import com.sapphire.hawe.mission.domain.GenerationSource;
import com.sapphire.hawe.mission.dto.ExperimentReport;
import com.sapphire.hawe.mission.dto.ExperimentReport.ArmSummary;
import com.sapphire.hawe.mission.dto.ExperimentReport.Comparison;
import com.sapphire.hawe.mission.dto.ExperimentReport.Paired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** AI 주제 vs 랜덤 주제 비교 리포트(B9). 닫힌 실험 미션만 센다. 계산은 같은 입력에 언제나 같은 결과다(부트스트랩 시드 고정). */
@Service
public class ExperimentReportService {

    static final int MIN_PAIRED_GROUPS = 5;
    static final int MIN_MISSIONS_PER_ARM = 30;
    static final int BOOTSTRAP_RESAMPLES = 5000;
    static final int BOOTSTRAP_BELOW_GROUPS = 30;
    static final long BOOTSTRAP_SEED = 20261013L;

    private final ExperimentMapper experiments;

    public ExperimentReportService(ExperimentMapper experiments) {
        this.experiments = experiments;
    }

    public ExperimentReport report(LocalDate from, LocalDate to) {
        return build(experiments.findClosedExperimentMissions(from, to, SeoulTime.now()), from, to);
    }

    static ExperimentReport build(List<ExperimentMissionRow> rows, LocalDate from, LocalDate to) {
        List<ExperimentMissionRow> ai = arm(rows, ExperimentArm.AI);
        List<ExperimentMissionRow> random = arm(rows, ExperimentArm.RANDOM);
        List<ExperimentMissionRow> aiPerProtocol = ai.stream()
                .filter(row -> GenerationSource.AI.name().equals(row.getGenerationSource())).toList();
        int fallback = ai.size() - aiPerProtocol.size();
        Paired paired = paired(ai, random);
        boolean underpowered = paired.groups() < MIN_PAIRED_GROUPS || ai.size() < MIN_MISSIONS_PER_ARM
                || random.size() < MIN_MISSIONS_PER_ARM;

        List<String> warnings = new ArrayList<>();
        if (ai.isEmpty() && random.isEmpty())
            warnings.add("기간 안에 닫힌 실험 미션이 없다. 실험이 켜져 있는지(hawe.experiment.topic.enabled)와 AI 키가 있는지 확인한다.");
        if (underpowered)
            warnings.add("표본이 작아 결과를 믿기 어렵다 — 두 군을 모두 겪은 그룹 " + paired.groups() + "개, 미션 AI "
                    + ai.size() + "개·랜덤 " + random.size() + "개 (그룹 " + MIN_PAIRED_GROUPS + "개, 군마다 미션 "
                    + MIN_MISSIONS_PER_ARM + "개 이상일 때 참고할 만하다).");
        warnings.add("같은 그룹의 미션끼리는 서로 닮아서, 미션을 하나씩 센 z 검정의 p 는 실제보다 작게 나온다. "
                + "그룹 안에서 AI 날과 랜덤 날을 비교한 값을 주 지표로 본다.");
        if (fallback > 0)
            warnings.add("AI 군 미션 " + fallback + "개는 AI 가 실패해 고정 문장이 나갔다. 배정대로 센 값(ITT)에는 넣고 "
                    + "per-protocol 값에서는 뺐다.");
        if (paired.excludedGroups() > 0)
            warnings.add("한쪽 군만 겪은 그룹 " + paired.excludedGroups() + "개는 대응 비교에서 뺐다.");

        return new ExperimentReport(from, to, underpowered, warnings, summary(ai), summary(random), compare(ai, random),
                summary(aiPerProtocol), compare(aiPerProtocol, random), fallback, paired);
    }

    private static List<ExperimentMissionRow> arm(List<ExperimentMissionRow> rows, ExperimentArm arm) {
        return rows.stream().filter(row -> arm.name().equals(row.getExperimentArm())).toList();
    }

    static ArmSummary summary(List<ExperimentMissionRow> rows) {
        long eligible = 0, submitted = 0, onTime = 0, engagement = 0;
        List<Double> firstMinutes = new ArrayList<>();
        for (ExperimentMissionRow row : rows) {
            eligible += row.getEligible();
            submitted += row.getSubmitted();
            onTime += row.getOnTime();
            engagement += row.getEngagement();
            if (row.getSubmitted() > 0 && row.getFirstSubmitSeconds() != null)
                firstMinutes.add(Math.max(0, row.getFirstSubmitSeconds()) / 60.0);
        }
        Map<String, long[]> groupDays = groupDays(rows);
        List<Double> dayRates = groupDays.values().stream().filter(sums -> sums[0] > 0)
                .map(sums -> (double) sums[1] / sums[0]).toList();
        return new ArmSummary(rows.size(), (int) rows.stream().map(ExperimentMissionRow::getGroupId).distinct().count(),
                groupDays.size(), eligible, submitted,
                eligible == 0 ? null : round((double) submitted / eligible, 4),
                round(ExperimentStats.wilson(submitted, eligible), 4),
                round(ExperimentStats.mean(dayRates), 4),
                submitted == 0 ? null : round((double) onTime / submitted, 4),
                rows.isEmpty() ? null : round((double) engagement / rows.size(), 2),
                round(ExperimentStats.median(firstMinutes), 1));
    }

    static Comparison compare(List<ExperimentMissionRow> ai, List<ExperimentMissionRow> random) {
        long aiEligible = ai.stream().mapToLong(ExperimentMissionRow::getEligible).sum();
        long aiSubmitted = ai.stream().mapToLong(ExperimentMissionRow::getSubmitted).sum();
        long randomEligible = random.stream().mapToLong(ExperimentMissionRow::getEligible).sum();
        long randomSubmitted = random.stream().mapToLong(ExperimentMissionRow::getSubmitted).sum();
        if (aiEligible == 0 || randomEligible == 0) return new Comparison(null, null, null);
        double difference = ((double) aiSubmitted / aiEligible - (double) randomSubmitted / randomEligible) * 100;
        double[] test = ExperimentStats.twoProportionZ(aiSubmitted, aiEligible, randomSubmitted, randomEligible);
        return new Comparison(round(difference, 2), test == null ? null : round(test[0], 3),
                test == null ? null : round(test[1], 4));
    }

    /** 그룹마다 (AI 날 평균 참여율 − 랜덤 날 평균 참여율), 두 군을 모두 겪은 그룹만. */
    static Paired paired(List<ExperimentMissionRow> ai, List<ExperimentMissionRow> random) {
        Map<Long, List<Double>> aiDays = dayRatesByGroup(ai);
        Map<Long, List<Double>> randomDays = dayRatesByGroup(random);
        List<Double> differences = new ArrayList<>();
        for (Map.Entry<Long, List<Double>> entry : aiDays.entrySet())
            if (randomDays.containsKey(entry.getKey()))
                differences.add((ExperimentStats.mean(entry.getValue()) - ExperimentStats.mean(randomDays.get(entry.getKey()))) * 100);
        var allGroups = new HashSet<>(aiDays.keySet());
        allGroups.addAll(randomDays.keySet());
        int positive = (int) differences.stream().filter(d -> d > 1e-9).count();
        int negative = (int) differences.stream().filter(d -> d < -1e-9).count();
        Interval bootstrap = differences.size() < BOOTSTRAP_BELOW_GROUPS
                ? ExperimentStats.bootstrapMeanCi(differences, BOOTSTRAP_RESAMPLES, new Random(BOOTSTRAP_SEED)) : null;
        return new Paired(differences.size(), allGroups.size() - differences.size(),
                round(ExperimentStats.mean(differences), 2), round(ExperimentStats.meanCi(differences), 2),
                round(bootstrap, 2), positive, negative, differences.size() - positive - negative,
                differences.isEmpty() ? null : round(ExperimentStats.signTestP(positive, negative), 4));
    }

    private static Map<Long, List<Double>> dayRatesByGroup(List<ExperimentMissionRow> rows) {
        Map<Long, List<Double>> byGroup = new LinkedHashMap<>();
        Map<String, Long> groupOf = new LinkedHashMap<>();
        for (ExperimentMissionRow row : rows) groupOf.put(row.getGroupId() + "|" + row.getMissionDate(), row.getGroupId());
        groupDays(rows).forEach((key, sums) -> {
            if (sums[0] > 0) byGroup.computeIfAbsent(groupOf.get(key), ignored -> new ArrayList<>()).add((double) sums[1] / sums[0]);
        });
        return byGroup;
    }

    // (그룹|날짜) → [대상 인원 합, 제출 합]
    private static Map<String, long[]> groupDays(List<ExperimentMissionRow> rows) {
        Map<String, long[]> days = new LinkedHashMap<>();
        for (ExperimentMissionRow row : rows) {
            long[] sums = days.computeIfAbsent(row.getGroupId() + "|" + row.getMissionDate(), ignored -> new long[2]);
            sums[0] += row.getEligible();
            sums[1] += row.getSubmitted();
        }
        return days;
    }

    private static Double round(Double value, int digits) {
        if (value == null) return null;
        double scale = Math.pow(10, digits);
        return Math.round(value * scale) / scale;
    }

    private static Interval round(Interval interval, int digits) {
        return interval == null ? null : new Interval(round(interval.low(), digits), round(interval.high(), digits));
    }

    /** 발표·문서에 바로 붙일 수 있는 Markdown. */
    public static String markdown(ExperimentReport report) {
        StringBuilder md = new StringBuilder();
        md.append("# AI 주제 vs 랜덤 주제 — 참여율 비교\n\n");
        md.append("기간: ").append(report.from()).append(" ~ ").append(report.to()).append(" (닫힌 실험 미션만)\n\n");
        if (report.underpowered()) md.append("> **주의: 표본이 작아 결과를 믿기 어렵다.** 아래 \"주의\"를 먼저 읽는다.\n\n");
        md.append("## 요약\n\n");
        md.append("- 참여율(배정대로 셈): AI ").append(percent(report.ai().participation())).append(" vs 랜덤 ")
                .append(percent(report.random().participation())).append(", 차이 ")
                .append(points(report.difference().differencePoints())).append(" (z 검정 p ")
                .append(p(report.difference().pValue())).append(")\n");
        Paired paired = report.paired();
        md.append("- 그룹 안 비교(주 지표): 그룹 ").append(paired.groups()).append("개, 평균 차이 ")
                .append(points(paired.meanDifferencePoints())).append(" (95% CI ").append(range(paired.ci95()))
                .append(paired.bootstrapCi95() == null ? "" : ", 부트스트랩 " + range(paired.bootstrapCi95()))
                .append(", 부호 검정 p ").append(p(paired.signTestP())).append(") — AI 날이 높은 그룹 ")
                .append(paired.positive()).append(", 낮은 그룹 ").append(paired.negative()).append(", 같은 그룹 ")
                .append(paired.ties()).append("\n\n");
        md.append("## 군별 지표\n\n| 지표 | AI | 랜덤 |\n|---|---|---|\n");
        ArmSummary ai = report.ai();
        ArmSummary random = report.random();
        row(md, "미션 수", ai.missions(), random.missions());
        row(md, "그룹 수", ai.groups(), random.groups());
        row(md, "(그룹, 날짜) 수", ai.groupDays(), random.groupDays());
        row(md, "대상 인원 합 / 제출 합", ai.eligible() + " / " + ai.submitted(), random.eligible() + " / " + random.submitted());
        row(md, "참여율 (95% CI)", percent(ai.participation()) + " (" + percentRange(ai.participationCi95()) + ")",
                percent(random.participation()) + " (" + percentRange(random.participationCi95()) + ")");
        row(md, "(그룹, 날짜) 평균 참여율", percent(ai.groupDayMeanParticipation()), percent(random.groupDayMeanParticipation()));
        row(md, "마감 전 제출 비율", percent(ai.onTimeRate()), percent(random.onTimeRate()));
        row(md, "미션당 반응+댓글", number(ai.engagementPerMission()), number(random.engagementPerMission()));
        row(md, "첫 제출까지 중앙값(분)", number(ai.medianFirstSubmitMinutes()), number(random.medianFirstSubmitMinutes()));
        md.append("\n## AI 가 실제로 문장을 만든 미션만 (per-protocol)\n\n");
        md.append("- AI 실패로 고정 문장이 나간 미션 ").append(report.aiFallbackMissions()).append("개를 빼고: AI ")
                .append(percent(report.aiPerProtocol().participation())).append(" vs 랜덤 ")
                .append(percent(random.participation())).append(", 차이 ")
                .append(points(report.perProtocolDifference().differencePoints())).append("\n\n");
        md.append("## 주의\n\n");
        for (String warning : report.warnings()) md.append("- ").append(warning).append('\n');
        return md.toString();
    }

    private static void row(StringBuilder md, String name, Object ai, Object random) {
        md.append("| ").append(name).append(" | ").append(ai).append(" | ").append(random).append(" |\n");
    }

    private static String percent(Double fraction) {
        return fraction == null ? "-" : String.format(Locale.ROOT, "%.1f%%", fraction * 100);
    }

    private static String percentRange(Interval interval) {
        return interval == null ? "-" : String.format(Locale.ROOT, "%.1f~%.1f%%", interval.low() * 100, interval.high() * 100);
    }

    private static String points(Double value) {
        return value == null ? "-" : String.format(Locale.ROOT, "%+.1f%%p", value);
    }

    private static String range(Interval interval) {
        return interval == null ? "-" : String.format(Locale.ROOT, "%+.1f ~ %+.1f%%p", interval.low(), interval.high());
    }

    private static String p(Double value) {
        if (value == null) return "-";
        return value < 0.001 ? "< 0.001" : String.format(Locale.ROOT, "%.3f", value);
    }

    private static String number(Double value) {
        return value == null ? "-" : String.format(Locale.ROOT, "%.1f", value);
    }
}
