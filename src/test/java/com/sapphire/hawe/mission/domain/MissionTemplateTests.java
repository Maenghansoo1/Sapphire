package com.sapphire.hawe.mission.domain;

import com.sapphire.hawe.global.config.MissionProperties;
import com.sapphire.hawe.mission.service.MissionService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MissionTemplateTests {

    @Test
    void phaseOneCatalogMatchesDocumentOrderAndHasPayloads() {
        var templates = MissionTemplate.values();
        assertEquals(13, templates.length);
        assertEquals(13, Arrays.stream(templates).map(Enum::name).collect(Collectors.toSet()).size());
        assertEquals(Set.of(1), Arrays.stream(templates).map(MissionTemplate::phase).collect(Collectors.toSet()));
        assertEquals(Set.of("PHOTO", "DRAW", "TEXT", "SLIDER"),
                Arrays.stream(templates).map(MissionTemplate::media).collect(Collectors.toSet()));
        assertEquals(List.of("PHOTO_NOW", "PHOTO_GALLERY", "PHOTO_COLOR", "TEXT_ACROSTIC", "TEXT_ONE_WORD",
                "TEXT_REVIEW", "TEXT_BALANCE", "TEXT_FILL_BLANK", "DRAW_SKETCH", "DRAW_A_TO_B",
                "SLIDER_POSITION", "SLIDER_TASTE", "SLIDER_CONSENSUS"),
                Arrays.stream(templates).map(Enum::name).toList());
        for (MissionTemplate template : templates) {
            assertFalse(template.payloadSchema().isEmpty());
        }
    }

    @Test
    void listTemplatesWrapsCatalogInContent() throws Exception {
        MissionService service = new MissionService(null, null, null, null, null,
                new MissionProperties(LocalTime.of(9, 0), LocalTime.of(23, 0), 5, MissionTemplateSelector.MVP_POOL), new ObjectMapper(), null);
        var response = service.listTemplates();
        assertEquals(13, response.content().size());
        assertEquals("TEXT_ACROSTIC", response.content().get(3).template());
        assertEquals("string(3)", response.content().get(3).paramsSchema().get("word"));
        assertEquals("string(required)", response.content().get(3).payloadSchema().get("text"));
        String json = new ObjectMapper().writeValueAsString(response);
        assertEquals(1, new ObjectMapper().readTree(json).size());
    }
}
