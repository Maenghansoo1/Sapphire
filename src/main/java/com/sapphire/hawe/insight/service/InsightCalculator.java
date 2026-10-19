package com.sapphire.hawe.insight.service;

import com.sapphire.hawe.insight.dao.InsightRow;
import com.sapphire.hawe.insight.domain.InsightPayload;
import com.sapphire.hawe.reaction.domain.StickerType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InsightCalculator {
    private final KeywordExtractor keywords;
    private final int topWords;
    private final int minWordCount;

    public InsightCalculator(KeywordExtractor keywords, int topWords, int minWordCount) {
        this.keywords = keywords;
        this.topWords = topWords;
        this.minWordCount = minWordCount;
    }

    public InsightPayload calculate(List<InsightRow> missions, List<InsightRow> submissions,
            List<InsightRow> reactions, List<InsightRow> comments, List<InsightRow> stickers) {
        if (missions.isEmpty()) return new InsightPayload(true, null, null, List.of(), null);
        Map<Long, InsightRow> missionById = new HashMap<>();
        long possible = 0;
        for (InsightRow mission : missions) {
            missionById.put(mission.getId(), mission);
            possible += Math.max(0, mission.getMemberCount());
        }
        if (possible == 0) return new InsightPayload(true, null, null, List.of(), null);
        Map<Long, Integer> reactionCounts = counts(reactions);
        Map<Long, Integer> commentCounts = counts(comments);
        long onTime = submissions.stream().filter(row -> !row.isLate()).count();
        int totalInteractions = reactionCounts.values().stream().mapToInt(Integer::intValue).sum()
                + commentCounts.values().stream().mapToInt(Integer::intValue).sum();
        double participation = Math.min(1.0, (double) submissions.size() / possible);
        double punctual = submissions.isEmpty() ? 0 : (double) onTime / submissions.size();
        double interaction = submissions.isEmpty() ? 0 : Math.min(1.0, (double) totalInteractions / (2 * submissions.size()));
        int score = (int) Math.max(0, Math.min(100, Math.round(100 * (0.6 * participation + 0.2 * punctual + 0.2 * interaction))));
        return new InsightPayload(false, new InsightPayload.Activity(score, participation, punctual, interaction),
                fastest(missions, submissions), words(submissions), mostReacted(submissions, missionById,
                        reactionCounts, commentCounts, stickers));
    }

    private InsightPayload.Fastest fastest(List<InsightRow> missions, List<InsightRow> submissions) {
        if (submissions.isEmpty()) return null;
        Map<Long, List<InsightRow>> byMission = new HashMap<>();
        Map<Long, List<Long>> delays = new HashMap<>();
        Map<Long, InsightRow> missionById = new HashMap<>();
        for (InsightRow mission : missions) missionById.put(mission.getId(), mission);
        for (InsightRow row : submissions) {
            byMission.computeIfAbsent(row.getMissionId(), ignored -> new ArrayList<>()).add(row);
            long seconds = Math.max(0, Duration.between(missionById.get(row.getMissionId()).getPublishedAt(),
                    row.getSubmittedAt()).getSeconds());
            delays.computeIfAbsent(row.getUserId(), ignored -> new ArrayList<>()).add(seconds);
        }
        Map<Long, Integer> firstCounts = new HashMap<>();
        for (List<InsightRow> entries : byMission.values()) {
            InsightRow first = entries.stream().min(Comparator.comparing(InsightRow::getSubmittedAt)
                    .thenComparing(InsightRow::getId)).orElseThrow();
            firstCounts.merge(first.getUserId(), 1, Integer::sum);
        }
        Long winner = firstCounts.keySet().stream().sorted(Comparator
                .<Long>comparingInt(id -> firstCounts.get(id)).reversed()
                .thenComparingDouble(id -> delays.get(id).stream().mapToLong(Long::longValue).average().orElse(0))
                .thenComparingLong(Long::longValue)).findFirst().orElseThrow();
        List<Long> times = delays.get(winner);
        return new InsightPayload.Fastest(winner, firstCounts.get(winner),
                Math.round(times.stream().mapToLong(Long::longValue).average().orElse(0)),
                times.stream().mapToLong(Long::longValue).min().orElse(0));
    }

    private List<InsightPayload.Word> words(List<InsightRow> submissions) {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Set<Long>> members = new HashMap<>();
        for (InsightRow row : submissions) {
            if (row.getTemplate() == null || !row.getTemplate().startsWith("TEXT_") || row.getText() == null) continue;
            for (String word : new HashSet<>(keywords.nouns(row.getText()))) {
                counts.merge(word, 1, Integer::sum);
                members.computeIfAbsent(word, ignored -> new HashSet<>()).add(row.getUserId());
            }
        }
        return counts.entrySet().stream().filter(entry -> entry.getValue() >= minWordCount)
                .map(entry -> new InsightPayload.Word(entry.getKey(), entry.getValue(), members.get(entry.getKey()).size()))
                .sorted(Comparator.comparingInt(InsightPayload.Word::count).reversed()
                        .thenComparing(Comparator.comparingInt(InsightPayload.Word::memberCount).reversed())
                        .thenComparing(InsightPayload.Word::word)).limit(topWords).toList();
    }

    private InsightPayload.MostReacted mostReacted(List<InsightRow> submissions, Map<Long, InsightRow> missions,
            Map<Long, Integer> reactions, Map<Long, Integer> comments, List<InsightRow> stickers) {
        InsightRow winner = submissions.stream().filter(row -> reactions.getOrDefault(row.getId(), 0)
                        + 2 * comments.getOrDefault(row.getId(), 0) > 0)
                .sorted(Comparator.<InsightRow>comparingInt(row -> reactions.getOrDefault(row.getId(), 0)
                        + 2 * comments.getOrDefault(row.getId(), 0)).reversed()
                        .thenComparing(InsightRow::getSubmittedAt).thenComparing(InsightRow::getId))
                .findFirst().orElse(null);
        if (winner == null) return null;
        String sticker = stickers.stream().filter(row -> winner.getId().equals(row.getSubmissionId()))
                .sorted(Comparator.comparingInt(InsightRow::getCount).reversed()
                        .thenComparingInt(row -> {
                            StickerType type = StickerType.fromValue(row.getStickerType());
                            return type == null ? Integer.MAX_VALUE : type.ordinal();
                        })).map(InsightRow::getStickerType).findFirst().orElse(null);
        InsightRow mission = missions.get(winner.getMissionId());
        return new InsightPayload.MostReacted(winner.getId(), winner.getMissionId(), mission.getContent(),
                winner.getTemplate(), winner.getUserId(), reactions.getOrDefault(winner.getId(), 0),
                comments.getOrDefault(winner.getId(), 0), sticker);
    }

    private static Map<Long, Integer> counts(List<InsightRow> rows) {
        Map<Long, Integer> result = new HashMap<>();
        for (InsightRow row : rows) result.put(row.getSubmissionId(), row.getCount());
        return result;
    }
}
