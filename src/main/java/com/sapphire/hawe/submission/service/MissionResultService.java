package com.sapphire.hawe.submission.service;

import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.service.GroupErrorCode;
import com.sapphire.hawe.mission.dao.MissionMapper;
import com.sapphire.hawe.mission.dao.MissionRow;
import com.sapphire.hawe.mission.service.MissionErrorCode;
import com.sapphire.hawe.submission.dao.SubmissionMapper;
import com.sapphire.hawe.submission.dao.SubmissionRow;
import com.sapphire.hawe.submission.dto.MissionResultResponse;
import com.sapphire.hawe.submission.dto.MissionResultResponse.ChoiceCount;
import com.sapphire.hawe.submission.dto.MissionResultResponse.NumberEntry;
import com.sapphire.hawe.submission.dto.MissionResultResponse.RankingEntry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 집계 틀(밸런스 게임·슬라이더 3종)의 그룹 결과. 내가 제출해야 볼 수 있고, 지금까지 들어온 제출로 계산한다(늦은 제출 포함).
 * 평균·차이는 정확한 값으로 비교하고 응답에서만 소수 첫째 자리로 반올림한다(HALF_UP).
 */
@Service
public class MissionResultService {
    private static final Set<String> SUPPORTED = Set.of("TEXT_BALANCE", "SLIDER_POSITION", "SLIDER_TASTE",
            "SLIDER_CONSENSUS");

    private final MissionMapper missions;
    private final GroupMemberMapper members;
    private final SubmissionMapper submissions;
    private final ObjectMapper json;

    public MissionResultService(MissionMapper missions, GroupMemberMapper members, SubmissionMapper submissions,
            ObjectMapper json) {
        this.missions = missions; this.members = members; this.submissions = submissions; this.json = json;
    }

    @Transactional(readOnly = true)
    public MissionResultResponse result(Long userId, Long missionId) {
        MissionRow mission = missions.findById(missionId, userId);
        if (mission == null) throw new BusinessException(MissionErrorCode.MISSION_NOT_FOUND);
        if (!members.existsByGroupIdAndUserId(mission.getGroupId(), userId))
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        if (!submissions.existsByMissionIdAndUserId(missionId, userId))
            throw new BusinessException(SubmissionErrorCode.FEED_LOCKED);
        String template = mission.getTemplate();
        if (!SUPPORTED.contains(template)) throw new BusinessException(MissionErrorCode.RESULT_NOT_SUPPORTED);
        List<SubmissionRow> rows = submissions.findResultRowsByMissionId(missionId);
        return switch (template) {
            case "TEXT_BALANCE" -> balance(rows, userId);
            case "SLIDER_CONSENSUS" -> consensus(rows);
            default -> slider(template, rows, userId);
        };
    }

    private static MissionResultResponse balance(List<SubmissionRow> rows, Long userId) {
        int a = 0, b = 0;
        String mine = null;
        for (SubmissionRow row : rows) {
            if ("A".equals(row.getChoiceValue())) a++;
            else if ("B".equals(row.getChoiceValue())) b++;
            if (row.getUserId().equals(userId)) mine = row.getChoiceValue();
        }
        return MissionResultResponse.balance(rows.size(), List.of(new ChoiceCount("A", a), new ChoiceCount("B", b)),
                mine);
    }

    private static MissionResultResponse slider(String template, List<SubmissionRow> rows, Long userId) {
        List<SubmissionRow> answered = rows.stream().filter(row -> row.getNumberValue() != null).toList();
        int count = answered.size();
        long sum = answered.stream().mapToLong(SubmissionRow::getNumberValue).sum();
        Integer mine = answered.stream().filter(row -> row.getUserId().equals(userId))
                .map(SubmissionRow::getNumberValue).findFirst().orElse(null);
        return MissionResultResponse.slider(template, rows.size(), count == 0 ? null : tenths(sum, count),
                answered.stream().map(row -> new NumberEntry(row.getUserId(), row.getNickname(), row.getNumberValue()))
                        .toList(),
                mine, mine == null ? null : tenths((long) mine * count - sum, count));
    }

    private MissionResultResponse consensus(List<SubmissionRow> rows) {
        List<SubmissionRow> answered = rows.stream().filter(row -> row.getNumberValue() != null).toList();
        int count = answered.size();
        long sum = answered.stream().mapToLong(SubmissionRow::getNumberValue).sum();
        // |guess − 평균| × count 를 정수로 비교한다 — 동률을 소수 오차 없이 가린다
        record Guess(SubmissionRow row, int guess, long scaledDiff) { }
        List<Guess> guesses = count == 0 ? List.of() : rows.stream()
                .map(row -> {
                    Integer guess = guessOf(row);
                    return guess == null ? null : new Guess(row, guess, Math.abs((long) guess * count - sum));
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(Guess::scaledDiff))  // 동률은 제출 순서(제출 시각 → id) 그대로
                .toList();
        long best = guesses.isEmpty() ? -1 : guesses.getFirst().scaledDiff();
        return MissionResultResponse.consensus(rows.size(), count == 0 ? null : tenths(sum, count),
                guesses.stream().map(g -> new RankingEntry(g.row().getUserId(), g.row().getNickname(), g.guess(),
                        tenths(g.scaledDiff(), count))).toList(),
                guesses.stream().filter(g -> g.scaledDiff() == best).map(g -> g.row().getUserId()).toList());
    }

    private Integer guessOf(SubmissionRow row) {
        if (row.getAnswerData() == null) return null;
        try {
            return json.readValue(row.getAnswerData(), Map.class).get("guess") instanceof Number value
                    ? value.intValue() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** value ÷ count 를 소수 첫째 자리로 (HALF_UP, 음수는 0에서 먼 쪽). */
    static double tenths(long value, long count) {
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP).doubleValue();
    }
}
