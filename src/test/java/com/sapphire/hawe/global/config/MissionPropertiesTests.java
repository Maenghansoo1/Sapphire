package com.sapphire.hawe.global.config;

import com.sapphire.hawe.mission.domain.MissionTemplate;
import com.sapphire.hawe.mission.domain.MissionTemplateSelector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class MissionPropertiesTests {

    @Autowired MissionProperties properties;

    @Test
    void applicationPropertiesKeepTheMvpPool() {
        assertEquals(MissionTemplateSelector.MVP_POOL, properties.enabledTemplates());
    }

    @Test
    void missingPoolFallsBackToMvpAndEmptyPoolIsRejected() {
        assertEquals(MissionTemplateSelector.MVP_POOL, create(null).enabledTemplates());
        assertThrows(IllegalArgumentException.class, () -> create(List.of()));
        List<MissionTemplate> configured = new ArrayList<>(List.of(MissionTemplate.SLIDER_TASTE));
        MissionProperties copied = create(configured);
        configured.add(MissionTemplate.PHOTO_NOW);
        assertEquals(List.of(MissionTemplate.SLIDER_TASTE), copied.enabledTemplates());
    }

    private static MissionProperties create(List<MissionTemplate> pool) {
        return new MissionProperties(LocalTime.of(9, 0), LocalTime.of(23, 0), 1, pool);
    }
}
