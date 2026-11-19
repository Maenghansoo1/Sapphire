package com.sapphire.hawe.search.domain;

import com.sapphire.hawe.global.text.KoreanJosa;
import com.sapphire.hawe.mission.domain.MissionTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 검색에 쓰는 미션 글(TODO_2/B1). <b>서버·AI 가 만든 값만</b> 넣는다:
 * 미션 문장, 틀 한국어 이름, params 의 서버 생성 값. 사용자 제출물·댓글·캡션·닉네임은 넣지 않는다.
 */
public final class MissionSearchText {
    /** params 중 서버·AI 가 만든 글 값. targetUserId(사람)·seconds(숫자)는 넣지 않는다. */
    public static final List<String> PARAM_KEYS =
            List.of("subject", "word", "condition", "color", "optionA", "optionB", "a", "b", "sentence");

    /** 검색 결과에 보여 줄 대상 이름. 검색에서는 닉네임을 풀지 않아(프라이버시) 누구를 가리켰는지 드러내지 않는다. */
    public static final String TARGET_DISPLAY = "친구";

    private MissionSearchText() { }

    /** 검색용 글. [대상] 자리는 빼서 "대상"이라는 낱말로 찾히지 않게 한다. params 를 못 읽었으면 null 을 넘기면 된다. */
    public static String of(String content, String template, Map<?, ?> params) {
        List<String> parts = new ArrayList<>();
        if (content != null) parts.add(content.replace(KoreanJosa.TARGET, " "));
        String name = templateName(template);
        if (name != null) parts.add(name);
        if (params != null) for (String key : PARAM_KEYS) {
            if (params.get(key) instanceof String value && !value.isBlank()) parts.add(value.replace(KoreanJosa.TARGET, " "));
        }
        return String.join(" ", parts);
    }

    /** 결과에 보여 줄 문장. [대상]은 닉네임 대신 "친구"로 바꾼다(조사도 맞춘다). */
    public static String display(String content) {
        return KoreanJosa.replaceTarget(content, TARGET_DISPLAY);
    }

    /** 부분 일치용으로 공백을 하나로 줄이고 소문자로. */
    public static String flatten(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }

    private static String templateName(String template) {
        if (template == null) return null;
        try { return MissionTemplate.valueOf(template).displayName(); }
        catch (IllegalArgumentException e) { return null; }
    }
}
