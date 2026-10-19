package com.sapphire.hawe.mission.domain;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * AI 가 돌려준 미션을 서버가 다시 검사한다 (MISSIONS_API §6 검증 표). 하나라도 어기면 {@link InvalidMission} —
 * 메시지는 사유만 담고 AI 가 쓴 문장은 담지 않는다(로그에 남기므로).
 */
public final class GeneratedMissionValidator {

    public static final int MAX_CONTENT = 60;
    public static final int MAX_PARAM = 40;
    public static final int MAX_CHOICE = 20;
    public static final double MAX_SIMILARITY = 0.8;
    public static final String TARGET = "[대상]";
    private static final Set<String> TOP_KEYS = Set.of("content", "category", "params");
    private static final Set<String> SHORT_KEYS = Set.of("optionA", "optionB");
    private static final Pattern HANGUL_3 = Pattern.compile("[가-힣]{3}");
    private static final Pattern BLANKS = Pattern.compile("_{3,}");
    private static final Pattern NUMBER = Pattern.compile("[0-9]{1,3}");
    private static final Pattern NOT_TEXT = Pattern.compile("[\\s\\p{P}\\p{S}]+");

    private GeneratedMissionValidator() {
    }

    public static GeneratedMission validate(Object raw, MissionTemplate template, boolean withTarget,
            List<String> recentContents, List<String> categories, List<String> blockedWords) {
        if (!(raw instanceof Map<?, ?> map) || !TOP_KEYS.equals(map.keySet()))
            throw new InvalidMission("결과 항목이 content·category·params 가 아님");
        String content = text(map.get("content"), "content", MAX_CONTENT, blockedWords);
        if (!(map.get("category") instanceof String category) || !categories.contains(category.strip()))
            throw new InvalidMission("카테고리가 목록에 없음");
        Map<String, Object> params = params(map.get("params"), template, blockedWords);

        switch (template) {
            case TEXT_ACROSTIC -> {
                String word = (String) params.get("word");
                if (!HANGUL_3.matcher(word).matches()) throw new InvalidMission("삼행시 제시어가 한글 세 글자가 아님");
                if (!content.contains(word)) throw new InvalidMission("삼행시 제시어가 문장에 없음");
            }
            case TEXT_BALANCE -> different(params, "optionA", "optionB", "밸런스 두 선택지가 같음");
            case TEXT_FILL_BLANK -> {
                String sentence = (String) params.get("sentence");
                var blanks = BLANKS.matcher(sentence).results().toList();
                if (blanks.size() != 1 || blanks.getFirst().group().length() != 3)
                    throw new InvalidMission("빈칸(___)이 정확히 한 개가 아님");
                if (withTarget && !sentence.contains(TARGET)) throw new InvalidMission("빈칸 문장에 [대상]이 없음");
            }
            default -> { }
        }

        boolean targetAnywhere = content.contains(TARGET)
                || params.values().stream().anyMatch(value -> value instanceof String s && s.contains(TARGET));
        if (withTarget && !content.contains(TARGET)) throw new InvalidMission("대상 미션인데 문장에 [대상]이 없음");
        if (!withTarget && targetAnywhere) throw new InvalidMission("대상이 없는데 [대상]을 씀");

        String normalized = normalize(content);
        for (String recent : recentContents) {
            String other = normalize(recent);
            if (normalized.equals(other) || similarity(normalized, other) >= MAX_SIMILARITY)
                throw new InvalidMission("최근 미션과 같거나 거의 같은 문장");
        }
        return new GeneratedMission(content, ((String) map.get("category")).strip(), params, GenerationSource.AI);
    }

    private static Map<String, Object> params(Object raw, MissionTemplate template, List<String> blockedWords) {
        if (!(raw instanceof Map<?, ?> given)) throw new InvalidMission("params 가 객체가 아님");
        Map<String, String> schema = new LinkedHashMap<>(template.paramsSchema());
        schema.remove("targetUserId");  // 서버가 정하는 값이라 AI 가 보내면 안 된다
        for (Object key : given.keySet())
            if (!(key instanceof String name) || !schema.containsKey(name))
                throw new InvalidMission("틀에 없는 빈칸: " + key);
        Map<String, Object> params = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : schema.entrySet()) {
            String key = entry.getKey();
            Object value = given.get(key);
            if (value == null) {
                if (entry.getValue().contains("optional")) continue;
                throw new InvalidMission("빈칸이 빠짐: " + key);
            }
            params.put(key, "seconds".equals(key) ? seconds(value)
                    : text(value, key, SHORT_KEYS.contains(key) ? MAX_CHOICE : MAX_PARAM, blockedWords));
        }
        return params;
    }

    private static int seconds(Object value) {
        String digits = value instanceof Number number ? String.valueOf(number) : String.valueOf(value).strip();
        if (!NUMBER.matcher(digits).matches()) throw new InvalidMission("seconds 가 숫자가 아님");
        int seconds = Integer.parseInt(digits);
        if (seconds < 10 || seconds > 60) throw new InvalidMission("seconds 가 10~60 밖");
        return seconds;
    }

    private static String text(Object value, String field, int max, List<String> blockedWords) {
        if (!(value instanceof String raw)) throw new InvalidMission(field + " 가 글이 아님");
        String text = raw.strip();
        int length = text.codePointCount(0, text.length());
        if (length == 0 || length > max) throw new InvalidMission(field + " 길이가 1~" + max + "자 밖");
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) throw new InvalidMission(field + " 에 줄바꿈");
        if (text.indexOf('{') >= 0 || text.indexOf('}') >= 0) throw new InvalidMission(field + " 에 중괄호");
        if (text.codePoints().anyMatch(GeneratedMissionValidator::emoji)) throw new InvalidMission(field + " 에 이모지");
        String lower = text.toLowerCase(Locale.ROOT);
        for (String word : blockedWords)
            if (!word.isBlank() && lower.contains(word.strip().toLowerCase(Locale.ROOT)))
                throw new InvalidMission(field + " 에 금칙어");
        return text;
    }

    // 그림 문자(이모지로 보이는 것)와 이모지로 바꾸는 표시만 막는다. ↔·★ 같은 글자 기호는 허용
    private static boolean emoji(int codePoint) {
        return Character.isEmojiPresentation(codePoint) || codePoint == 0xFE0F || Character.isEmojiModifier(codePoint);
    }

    private static void different(Map<String, Object> params, String a, String b, String reason) {
        if (normalize((String) params.get(a)).equals(normalize((String) params.get(b)))) throw new InvalidMission(reason);
    }

    /** 공백·문장부호·기호를 빼고 소문자로. */
    static String normalize(String text) {
        return NOT_TEXT.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** 두 글자씩 끊은 조각의 자카드 유사도 (0~1). */
    static double similarity(String a, String b) {
        Set<String> left = bigrams(a);
        Set<String> right = bigrams(b);
        if (left.isEmpty() && right.isEmpty()) return 1;
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        left.retainAll(right);
        return (double) left.size() / union.size();
    }

    private static Set<String> bigrams(String text) {
        int[] points = text.codePoints().toArray();
        Set<String> grams = new HashSet<>();
        if (points.length == 1) grams.add(new String(points, 0, 1));
        for (int i = 0; i + 1 < points.length; i++) grams.add(new String(points, i, 2));
        return grams;
    }

    public static final class InvalidMission extends RuntimeException {
        public InvalidMission(String reason) {
            super(reason);
        }
    }
}
