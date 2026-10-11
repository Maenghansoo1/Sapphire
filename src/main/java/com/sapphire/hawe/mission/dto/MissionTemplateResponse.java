package com.sapphire.hawe.mission.dto;

import java.util.Map;

public record MissionTemplateResponse(String template, String media, String name, int phase,
        Map<String, String> paramsSchema, Map<String, String> payloadSchema) {
}
