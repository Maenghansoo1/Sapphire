package com.sapphire.hawe.dev.service;

import com.sapphire.hawe.mission.dao.MissionRow;
import com.sapphire.hawe.mission.domain.MissionTemplate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class DevActionServiceTests {
    @Test void sampleMatchesAllSupportedTemplates() {
        DevActionService service = new DevActionService(null, null, null, null, null, null, null,
                new ObjectMapper());
        for (MissionTemplate template : MissionTemplate.values()) {
            MissionRow mission = new MissionRow();
            mission.setTemplate(template.name());
            mission.setParams("{\"word\":\"고양이\"}");
            var sample = service.sample(mission);
            if (template.media().equals("PHOTO") || template.media().equals("DRAW")) {
                assertTrue(sample.fields().isEmpty(), template.name());
                assertEquals("image/png", sample.files().getFirst("file").getContentType());
                assertTrue(sample.files().getFirst("file").getSize() > 0);
            } else {
                assertTrue(sample.files().isEmpty(), template.name());
                assertFalse(sample.fields().isEmpty(), template.name());
            }
            if (template == MissionTemplate.TEXT_ACROSTIC)
                assertEquals("고 테스트 문장\n양 테스트 문장\n이 테스트 문장", sample.fields().getFirst("text"));
        }
    }
}
