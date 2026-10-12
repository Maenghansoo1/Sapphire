package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.mission.domain.MissionTemplate;
import com.sapphire.hawe.mission.domain.MissionTemplateSelector;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
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
                    var result = generator.generate(template, seed, null, withTarget);
                    assertEquals(result, generator.generate(template, seed, null, withTarget));
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
                    assertNotEquals(result.content(), generator.generate(template, seed, result.content(),
                            withTarget).content());
                    contents.add(result.content());
                }
            }
            assertTrue(contents.size() >= 3);
        }
    }
}
