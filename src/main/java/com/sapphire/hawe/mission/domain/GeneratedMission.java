package com.sapphire.hawe.mission.domain;

import java.util.Map;

public record GeneratedMission(String content, String category, Map<String, Object> params, GenerationSource source) {

    /** 고정 문장 생성기용. */
    public GeneratedMission(String content, String category, Map<String, Object> params) {
        this(content, category, params, GenerationSource.PLACEHOLDER);
    }

    public GeneratedMission withSource(GenerationSource source) {
        return new GeneratedMission(content, category, params, source);
    }
}
