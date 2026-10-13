package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.mission.domain.GenerationContext;
import com.sapphire.hawe.mission.domain.GenerationSource;
import com.sapphire.hawe.mission.domain.GroupSignals;
import com.sapphire.hawe.mission.domain.MissionTemplate;
import com.sapphire.hawe.mission.domain.MissionTemplateSelector;
import com.sapphire.hawe.mission.domain.RecentMission;
import com.sapphire.hawe.mission.domain.SpecialDay;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderMissionGeneratorTests {
    private final PlaceholderMissionGenerator generator = new PlaceholderMissionGenerator();

    @Test void everyMvpTemplateHasValidDeterministicVariants() {
        for (MissionTemplate template : MissionTemplateSelector.MVP_POOL) {
            Set<String> contents = new HashSet<>();
            for (long seed = 0; seed < 100; seed++) {
                for (boolean withTarget : new boolean[] {false, true}) {
                    var result = generator.generate(template, seed, recent(), withTarget);
                    assertEquals(result, generator.generate(template, seed, recent(), withTarget));
                    assertEquals(template.paramsSchema().keySet().stream().filter(key -> !key.equals("targetUserId"))
                            .collect(java.util.stream.Collectors.toSet()), result.params().keySet());
                    assertTrue(result.category().length() <= 50 && !result.category().isBlank());
                    if (template == MissionTemplate.TEXT_FILL_BLANK) {
                        String sentence = (String) result.params().get("sentence");
                        assertEquals(1, sentence.split("___", -1).length - 1);
                        assertTrue(result.content().contains("[대상]"));
                    }
                    if (template == MissionTemplate.TEXT_ACROSTIC)
                        assertTrue(((String) result.params().get("word")).matches("[가-힣]{3}"));
                    if (template == MissionTemplate.DRAW_SKETCH || template == MissionTemplate.DRAW_A_TO_B)
                        assertEquals(withTarget, result.content().contains("[대상]"));
                    assertNotEquals(result.content(), generator.generate(template, seed, recent(result.content()),
                            withTarget).content());
                    contents.add(result.content());
                }
            }
            assertTrue(contents.size() >= 3);
        }
    }

    @Test void newTemplatesHaveValidVariants() {
        for (MissionTemplate template : List.of(MissionTemplate.TEXT_ONE_WORD, MissionTemplate.TEXT_REVIEW,
                MissionTemplate.TEXT_BALANCE, MissionTemplate.SLIDER_POSITION, MissionTemplate.SLIDER_TASTE,
                MissionTemplate.SLIDER_CONSENSUS)) {
            Set<String> contents = new HashSet<>();
            for (long seed = 0; seed < 100; seed++) {
                var result = generator.generate(template, seed, recent(), false);
                assertEquals(result, generator.generate(template, seed, recent(), false));
                assertEquals(template.paramsSchema().keySet(), result.params().keySet());
                assertTrue(result.category().length() <= 50 && !result.category().isBlank());
                assertFalse(result.content().contains("[대상]"));
                if (template == MissionTemplate.TEXT_BALANCE)
                    assertNotEquals(result.params().get("optionA"), result.params().get("optionB"));
                if (template.media().equals("SLIDER")) {
                    String left = (String) result.params().get("left");
                    String right = (String) result.params().get("right");
                    assertNotEquals(left, right);
                    assertTrue(!left.isBlank() && left.length() <= 20 && !right.isBlank() && right.length() <= 20);
                }
                assertNotEquals(result.content(), generator.generate(template, seed, recent(result.content()), false).content());
                contents.add(result.content());
            }
            assertTrue(contents.size() >= 3, template.name());
        }
    }

    @Test void avoidsRecentSentencesAndReusesTheLeastRecentWhenAllWereUsed() {
        for (MissionTemplate template : MissionTemplate.values()) {
            for (long seed = 0; seed < 50; seed++) {
                var first = generator.generate(template, seed, recent(), false);
                var second = generator.generate(template, seed, recent(first.content()), false);
                var third = generator.generate(template, seed, recent(second.content(), first.content()), false);
                assertEquals(3, Set.of(first.content(), second.content(), third.content()).size(), template.name());
                // 변형 3개를 모두 최근에 썼으면 가장 오래전에 쓴 것을 다시 쓴다 (예외 없음, 바로 앞 문장은 피함)
                assertEquals(first, generator.generate(template, seed,
                        recent(third.content(), second.content(), first.content()), false));
                assertEquals(second, generator.generate(template, seed,
                        recent("다른 틀 문장", first.content(), third.content(), second.content(), "또 다른 문장"), false));
            }
        }
    }

    @Test void ignoresGroupDetailsAndMarksItsSource() {
        var plain = generator.generate(MissionTemplate.PHOTO_NOW, 3, recent(), false);
        var withGroup = generator.generate(MissionTemplate.PHOTO_NOW, 3, new GenerationContext(LocalDate.of(2026, 10, 13),
                "COUPLE", List.of(), List.of(new SpecialDay(SpecialDay.Kind.COUPLE_MILESTONE, "우리의 100일", 0)),
                GroupSignals.none(2, 100)), false);
        assertEquals(plain, withGroup);
        assertEquals(GenerationSource.PLACEHOLDER, plain.source());
        assertEquals(plain, generator.generate(MissionTemplate.PHOTO_NOW, 3, null, false));
    }

    /** 최근 미션 문장만 있는 생성 정보 (최근 것부터). */
    private static GenerationContext recent(String... contents) {
        return new GenerationContext(LocalDate.of(2026, 10, 13), "FRIEND",
                Arrays.stream(contents).map(content -> new RecentMission(content, "일상", "PHOTO_NOW")).toList(),
                List.of(), GroupSignals.none(1, 1));
    }
}
