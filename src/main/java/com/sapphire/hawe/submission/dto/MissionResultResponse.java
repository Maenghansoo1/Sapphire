package com.sapphire.hawe.submission.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 집계(V 유형) 결과 — 지금은 밸런스 게임만 (MISSIONS_API §5). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MissionResultResponse(String template, int totalCount, List<ChoiceCount> choices, String myChoice) {

    public record ChoiceCount(String choice, int count) { }

    public static MissionResultResponse balance(int totalCount, List<ChoiceCount> choices, String myChoice) {
        return new MissionResultResponse("TEXT_BALANCE", totalCount, choices, myChoice);
    }
}
