package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.config.MissionAiProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API 를 직접 부른다 (Spring AI 미사용). 도구 하나를 반드시 쓰게 해서 결과를 JSON 객체로 받는다.
 * 긴 시스템 프롬프트는 캐시해 두 번째 호출부터 싸게 한다. 실패는 모두 {@link AiGenerationException}.
 */
@Component
public class AnthropicClient {

    static final String VERSION = "2023-06-01";

    private final MissionAiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient rest;

    @Autowired
    public AnthropicClient(MissionAiProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, RestClient.builder().requestFactory(requestFactory(properties)).build());
    }

    AnthropicClient(MissionAiProperties properties, ObjectMapper objectMapper, RestClient rest) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.rest = rest;
    }

    /** {@code tool} 을 강제로 부르게 하고 그 입력(JSON 객체)을 돌려준다. */
    public Map<String, Object> callTool(String system, String userMessage, Map<String, Object> tool) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("max_tokens", properties.maxTokens());
        body.put("temperature", properties.temperature());
        body.put("system", List.of(Map.of("type", "text", "text", system, "cache_control", Map.of("type", "ephemeral"))));
        body.put("tools", List.of(tool));
        body.put("tool_choice", Map.of("type", "tool", "name", tool.get("name")));
        body.put("messages", List.of(Map.of("role", "user", "content", userMessage)));

        String response;
        try {
            response = rest.post().uri(properties.baseUrl() + "/v1/messages")
                    .header("x-api-key", properties.apiKey())
                    .header("anthropic-version", VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            throw new AiGenerationException("AI 응답 오류 HTTP " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new AiGenerationException("AI 연결 실패 또는 시간 초과");
        } catch (RestClientException e) {
            throw new AiGenerationException("AI 호출 실패");
        }
        return toolInput(response, String.valueOf(tool.get("name")));
    }

    private Map<String, Object> toolInput(String response, String toolName) {
        JsonNode root;
        try {
            root = response == null ? null : objectMapper.readTree(response);
        } catch (JacksonException e) {
            throw new AiGenerationException("AI 응답이 JSON 이 아님");
        }
        if (root == null || !root.isObject()) throw new AiGenerationException("AI 응답이 비어 있음");
        if ("max_tokens".equals(root.path("stop_reason").asString(""))) throw new AiGenerationException("AI 응답이 길이 제한으로 잘림");
        for (JsonNode block : root.path("content")) {
            if ("tool_use".equals(block.path("type").asString("")) && toolName.equals(block.path("name").asString(""))) {
                JsonNode input = block.path("input");
                if (!input.isObject()) throw new AiGenerationException("AI 도구 입력이 객체가 아님");
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.convertValue(input, Map.class);
                return map;
            }
        }
        throw new AiGenerationException("AI 응답에 도구 결과가 없음");
    }

    private static JdkClientHttpRequestFactory requestFactory(MissionAiProperties properties) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}
