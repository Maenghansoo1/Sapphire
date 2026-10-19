package com.sapphire.hawe.mission.domain;

import java.util.Map;

public enum MissionTemplate {
    PHOTO_NOW("PHOTO", "지금 이 순간", Map.of("subject", "string"), Map.of("file", "image(required)")),
    PHOTO_GALLERY("PHOTO", "갤러리 발굴", Map.of("condition", "string"), Map.of("file", "image(required)")),
    PHOTO_COLOR("PHOTO", "색깔 사냥", Map.of("color", "string"), Map.of("file", "image(required)")),
    TEXT_ACROSTIC("TEXT", "삼행시", Map.of("word", "string(3)"), Map.of("text", "string(required)")),
    TEXT_ONE_WORD("TEXT", "한 단어 일기", Map.of(), Map.of("text", "string(required)", "caption", "string(optional)")),
    TEXT_REVIEW("TEXT", "한 줄 후기", Map.of(), Map.of("number", "int(1..5, required)", "text", "string(required)")),
    TEXT_BALANCE("TEXT", "밸런스 게임", Map.of("optionA", "string", "optionB", "string"), Map.of("choice", "A|B(required)", "caption", "string(optional)")),
    TEXT_FILL_BLANK("TEXT", "빈칸 채우기", Map.of("sentence", "string(one ___)", "targetUserId", "long(server-assigned)"), Map.of("text", "string(required)")),
    DRAW_SKETCH("DRAW", "30초 스케치", Map.of("subject", "string(optional)", "targetUserId", "long(server-assigned, optional)", "seconds", "int"), Map.of("file", "image(required)")),
    DRAW_A_TO_B("DRAW", "A를 B로", Map.of("a", "string(optional)", "targetUserId", "long(server-assigned, optional)", "b", "string"), Map.of("file", "image(required)"));

    private final String media;
    private final String name;
    private final Map<String, String> paramsSchema;
    private final Map<String, String> payloadSchema;

    MissionTemplate(String media, String name, Map<String, String> paramsSchema, Map<String, String> payloadSchema) {
        this.media = media;
        this.name = name;
        this.paramsSchema = paramsSchema;
        this.payloadSchema = payloadSchema;
    }

    public String media() {
        return media;
    }

    public String displayName() {
        return name;
    }

    public int phase() {
        return 1;
    }

    public Map<String, String> paramsSchema() {
        return paramsSchema;
    }

    public Map<String, String> payloadSchema() {
        return payloadSchema;
    }
}
