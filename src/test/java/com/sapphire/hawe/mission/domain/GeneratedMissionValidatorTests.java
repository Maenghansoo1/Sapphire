package com.sapphire.hawe.mission.domain;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.sapphire.hawe.mission.domain.MissionTemplate.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedMissionValidatorTests {

    private static final List<String> CATEGORIES = List.of("일상", "감성", "추억", "유머");

    @Test void promptExamplesPassForEveryTemplate() {
        // 팀원 프롬프트의 "좋은 예시"는 모두 통과해야 한다 (서버 검증이 프롬프트와 어긋나지 않게)
        for (MissionTemplate template : MissionTemplate.values()) {
            boolean target = template == TEXT_FILL_BLANK;
            GeneratedMission mission = validate(valid(template), template, target);
            assertEquals(GenerationSource.AI, mission.source(), template.name());
            assertEquals("일상", mission.category());
        }
        GeneratedMission sketch = validate(valid(DRAW_SKETCH), DRAW_SKETCH, false);
        assertEquals(Map.of("subject", "고양이", "seconds", 30), sketch.params());
    }

    @Test void valuesAreTrimmedAndSecondsBecomeNumbers() {
        Map<String, Object> raw = valid(DRAW_SKETCH);
        raw.put("content", "  30초 안에 고양이 그리기. 시작!  ");
        raw.put("category", " 일상 ");
        raw.put("params", new LinkedHashMap<>(Map.of("subject", " 고양이 ", "seconds", "20")));
        GeneratedMission mission = validate(raw, DRAW_SKETCH, false);
        assertEquals("30초 안에 고양이 그리기. 시작!", mission.content());
        assertEquals("일상", mission.category());
        assertEquals(Map.of("subject", "고양이", "seconds", 20), mission.params());
    }

    @Test void resultMustHaveExactlyTheThreeFields() {
        rejects(null, PHOTO_NOW, false);
        rejects("문장만", PHOTO_NOW, false);
        Map<String, Object> extra = valid(PHOTO_NOW);
        extra.put("explanation", "이렇게 만들었어");
        rejects(extra, PHOTO_NOW, false);
        Map<String, Object> missing = valid(PHOTO_NOW);
        missing.remove("params");
        rejects(missing, PHOTO_NOW, false);
        Map<String, Object> notObject = valid(PHOTO_NOW);
        notObject.put("params", List.of("subject"));
        rejects(notObject, PHOTO_NOW, false);
    }

    @Test void contentRules() {
        assertContentRejected("   ");
        assertContentRejected("가".repeat(61));
        assertContentRejected("지금 찍어 와\n두 줄");
        assertContentRejected("지금 찍어 와 😀");
        assertContentRejected("지금 찍어 와 ❤️");          // 이모지로 바꾸는 표시(U+FE0F)
        assertContentRejected("지금 {subject} 찍어 와");
        assertContentRejected("술 한 잔 찍어 와");           // 금칙어
        assertContentRejected(123);
        assertEquals("가".repeat(60), validate(withContent("가".repeat(60)), PHOTO_NOW, false).content());
        // 화살표·별 같은 글자 기호는 괜찮다 (팀원 프롬프트 예시에 ↔ 가 있다)
        validate(withContent("오늘 컨디션 몇 %? 방전 ↔ 풀충전 ★"), PHOTO_NOW, false);
    }

    @Test void categoryMustBeFromTheList() {
        Map<String, Object> raw = valid(PHOTO_NOW);
        raw.put("category", "감동");
        rejects(raw, PHOTO_NOW, false);
        raw.put("category", 1);
        rejects(raw, PHOTO_NOW, false);
    }

    @Test void paramsMustMatchTheTemplate() {
        rejects(withParams(PHOTO_NOW, Map.of()), PHOTO_NOW, false);                                    // 빠짐
        rejects(withParams(PHOTO_NOW, Map.of("subject", "컵", "color", "노란색")), PHOTO_NOW, false);  // 남는 빈칸
        rejects(withParams(PHOTO_NOW, Map.of("subject", 3)), PHOTO_NOW, false);                         // 글이 아님
        rejects(withParams(PHOTO_NOW, Map.of("subject", "가".repeat(41))), PHOTO_NOW, false);
        rejects(withParams(TEXT_ONE_WORD, Map.of("word", "설렘")), TEXT_ONE_WORD, false);              // 빈칸 없는 틀
        // 대상은 서버가 정한다 — AI 가 targetUserId 를 보내면 거부
        Map<String, Object> target = new HashMap<>(Map.of("sentence", "[대상]의 숨겨진 재능은 ___", "targetUserId", 3));
        rejects(withParams(TEXT_FILL_BLANK, target), TEXT_FILL_BLANK, true);
        // optional 빈칸은 없어도 된다 (대상 스케치: subject 없이 seconds 만)
        Map<String, Object> sketch = withParams(DRAW_SKETCH, Map.of("seconds", 30));
        sketch.put("content", "30초 안에 [대상] 그리기. 최대한 멋지게");
        assertEquals(Map.of("seconds", 30), validate(sketch, DRAW_SKETCH, true).params());
        for (Object seconds : List.of(5, 61, "삼십", "30초", 30.5))
            rejects(withParams(DRAW_SKETCH, Map.of("subject", "고양이", "seconds", seconds)), DRAW_SKETCH, false);
    }

    @Test void acrosticWordIsThreeHangulSyllablesAndInTheSentence() {
        for (String word : List.of("퇴근", "월요일요", "abc", "퇴근ㄱ"))
            rejects(withParams(TEXT_ACROSTIC, Map.of("word", word)), TEXT_ACROSTIC, false);
        Map<String, Object> raw = valid(TEXT_ACROSTIC);
        raw.put("content", "오늘의 삼행시 가자");
        rejects(raw, TEXT_ACROSTIC, false);
    }

    @Test void balanceChoicesMustDiffer() {
        rejects(withParams(TEXT_BALANCE, Map.of("optionA", "평생 여름", "optionB", " 평생여름! ")), TEXT_BALANCE, false);
        rejects(withParams(TEXT_BALANCE, Map.of("optionA", "가".repeat(21), "optionB", "평생 겨울")), TEXT_BALANCE, false);
    }

    @Test void fillBlankHasExactlyOneBlankAndTheTarget() {
        for (String sentence : List.of("[대상]의 숨겨진 재능은?", "[대상]의 ___ 재능은 ___", "[대상]의 숨겨진 재능은 ____",
                "숨겨진 재능은 ___"))
            rejects(withParams(TEXT_FILL_BLANK, Map.of("sentence", sentence)), TEXT_FILL_BLANK, true);
    }

    @Test void targetMarkerFollowsTheServerDecision() {
        Map<String, Object> noTargetInSentence = valid(TEXT_FILL_BLANK);
        noTargetInSentence.put("content", "숨겨진 재능을 채워 줘");
        rejects(noTargetInSentence, TEXT_FILL_BLANK, true);
        rejects(withContent("[대상]의 책상 위 TMI 찍어 와"), PHOTO_NOW, false);
        rejects(withParams(DRAW_A_TO_B, Map.of("a", "[대상]", "b", "아이돌")), DRAW_A_TO_B, false);
        Map<String, Object> drawTarget = withParams(DRAW_A_TO_B, Map.of("a", "[대상]", "b", "판타지 게임 캐릭터"));
        drawTarget.put("content", "판타지 게임 캐릭터 버전의 [대상]");
        assertEquals("[대상]", validate(drawTarget, DRAW_A_TO_B, true).params().get("a"));
    }

    @Test void nearCopiesOfRecentMissionsAreRejected() {
        Map<String, Object> raw = valid(PHOTO_NOW);
        String content = (String) raw.get("content");
        assertThrows(GeneratedMissionValidator.InvalidMission.class, () -> GeneratedMissionValidator.validate(raw,
                PHOTO_NOW, false, List.of(" " + content.replace(" ", "") + "!! "), CATEGORIES, List.of()));
        // 두 글자 조각 9개 중 8개가 같으면 0.8 → 거부, 8개 중 7개면 0.78 → 통과
        assertEquals(0.8, GeneratedMissionValidator.similarity("가나다라마바사아자차", "가나다라마바사아자카"), 1e-9);
        assertTrue(GeneratedMissionValidator.similarity("가나다라마바사아자", "가나다라마바사아카") < 0.8);
        assertThrows(GeneratedMissionValidator.InvalidMission.class, () -> GeneratedMissionValidator.validate(
                withContent("가나다라마바사아자차"), PHOTO_NOW, false, List.of("가나다라마바사아자카"), CATEGORIES, List.of()));
        GeneratedMissionValidator.validate(withContent("가나다라마바사아자"), PHOTO_NOW, false, List.of("가나다라마바사아카"),
                CATEGORIES, List.of());
        assertEquals("오늘컨디션몇방전풀충전", GeneratedMissionValidator.normalize("오늘 컨디션 몇 %? 방전 ↔ 풀충전"));
    }

    @Test void rejectionMessagesDoNotRepeatTheAiText() {
        var error = assertThrows(GeneratedMissionValidator.InvalidMission.class,
                () -> validate(withContent("비밀스러운문장 😀"), PHOTO_NOW, false));
        assertFalse(error.getMessage().contains("비밀스러운문장"));
    }

    private static void assertContentRejected(Object content) {
        Map<String, Object> raw = valid(PHOTO_NOW);
        raw.put("content", content);
        rejects(raw, PHOTO_NOW, false);
    }

    private static GeneratedMission validate(Object raw, MissionTemplate template, boolean withTarget) {
        return GeneratedMissionValidator.validate(raw, template, withTarget, List.of("오늘 하늘 사진 찍어 줘"), CATEGORIES,
                List.of("술", "도박"));
    }

    private static void rejects(Object raw, MissionTemplate template, boolean withTarget) {
        assertThrows(GeneratedMissionValidator.InvalidMission.class, () -> validate(raw, template, withTarget));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> params(Map<String, Object> raw) {
        return (Map<String, Object>) raw.get("params");
    }

    private static Map<String, Object> withContent(Object content) {
        Map<String, Object> raw = valid(PHOTO_NOW);
        raw.put("content", content);
        return raw;
    }

    private static Map<String, Object> withParams(MissionTemplate template, Map<String, Object> params) {
        Map<String, Object> raw = valid(template);
        raw.put("params", params);
        return raw;
    }

    /** 팀원 프롬프트(mission-template-prompt)의 좋은 예시. */
    static Map<String, Object> valid(MissionTemplate template) {
        return switch (template) {
            case PHOTO_NOW -> raw("지금 내 손 닿는 곳의 제일 만만한 물건 찍어 와", "subject", "손 닿는 곳에서 제일 만만한 물건");
            case PHOTO_GALLERY -> raw("갤러리에서 제일 최근에 저장한 짤 공개", "condition", "가장 최근에 저장한 짤");
            case PHOTO_COLOR -> raw("오늘 눈에 띈 노란색 중 원픽 하나 찍어 와", "color", "노란색");
            case TEXT_ACROSTIC -> raw("'떡볶이' 삼행시로 오늘 마무리", "word", "떡볶이");
            case TEXT_ONE_WORD -> raw("오늘을 한 단어로 요약하면? 이유도 한 줄");
            case TEXT_REVIEW -> raw("오늘 점심 별점 몇 개? 한 줄 평도");
            case TEXT_BALANCE -> raw("평생 여름 vs 평생 겨울, 골라", "optionA", "평생 여름", "optionB", "평생 겨울");
            case TEXT_FILL_BLANK -> raw("[대상]의 숨겨진 재능은 ___. 다들 채워 줘", "sentence", "[대상]의 숨겨진 재능은 ___");
            case DRAW_SKETCH -> raw("30초 안에 고양이 그리기. 시작!", "subject", "고양이", "seconds", 30);
            case DRAW_A_TO_B -> raw("우리 집 냉장고가 아이돌로 데뷔한다면?", "a", "냉장고", "b", "아이돌");
        };
    }

    private static Map<String, Object> raw(String content, Object... params) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < params.length; i += 2) values.put((String) params[i], params[i + 1]);
        Map<String, Object> raw = new HashMap<>();
        raw.put("content", content);
        raw.put("category", "일상");
        raw.put("params", values);
        return raw;
    }
}
