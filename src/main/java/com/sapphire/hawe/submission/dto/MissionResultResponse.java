package com.sapphire.hawe.submission.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 집계(V 유형) 결과. 틀마다 쓰는 필드만 채운다 (MISSIONS_API §5). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MissionResultResponse(String template, int totalCount,
        List<ChoiceCount> choices, String myChoice,
        Double average, List<NumberEntry> distribution, Integer myNumber, Double diffFromAverage,
        List<RankingEntry> ranking, List<Long> winnerUserIds) {

    public record ChoiceCount(String choice, int count) { }
    public record NumberEntry(Long userId, String nickname, int number) { }
    public record RankingEntry(Long userId, String nickname, int guess, double diff) { }

    public static MissionResultResponse balance(int totalCount, List<ChoiceCount> choices, String myChoice) {
        return new MissionResultResponse("TEXT_BALANCE", totalCount, choices, myChoice,
                null, null, null, null, null, null);
    }

    public static MissionResultResponse slider(String template, int totalCount, Double average,
            List<NumberEntry> distribution, Integer myNumber, Double diffFromAverage) {
        return new MissionResultResponse(template, totalCount, null, null,
                average, distribution, myNumber, diffFromAverage, null, null);
    }

    public static MissionResultResponse consensus(int totalCount, Double average, List<RankingEntry> ranking,
            List<Long> winnerUserIds) {
        return new MissionResultResponse("SLIDER_CONSENSUS", totalCount, null, null,
                average, null, null, null, ranking, winnerUserIds);
    }
}
