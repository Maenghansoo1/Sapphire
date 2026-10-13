package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.config.MissionAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.SocketTimeoutException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AnthropicClientTests {

    private static final String KEY = "sk-ant-test-secret";
    private static final Map<String, Object> TOOL = Map.of("name", "submit_mission", "input_schema", Map.of("type", "object"));

    private MockRestServiceServer server;
    private AnthropicClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        MissionAiProperties properties = new MissionAiProperties(true, "https://api.anthropic.com/", KEY, null, 300, 0.7,
                null, null, null, null, null, null, null, null);
        client = new AnthropicClient(properties, new ObjectMapper(), builder.build());
    }

    @Test
    void sendsCachedSystemPromptAndForcedToolThenReturnsToolInput() {
        server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", KEY))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("claude-haiku-5-5"))
                .andExpect(jsonPath("$.max_tokens").value(300))
                .andExpect(jsonPath("$.temperature").value(0.7))
                .andExpect(jsonPath("$.system[0].text").value("시스템 프롬프트"))
                .andExpect(jsonPath("$.system[0].cache_control.type").value("ephemeral"))
                .andExpect(jsonPath("$.tools[0].name").value("submit_mission"))
                .andExpect(jsonPath("$.tool_choice.type").value("tool"))
                .andExpect(jsonPath("$.tool_choice.name").value("submit_mission"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("입력 메시지"))
                .andRespond(withSuccess("""
                        { "id": "msg_1", "type": "message", "stop_reason": "tool_use",
                          "content": [ { "type": "text", "text": "만들었어" },
                                       { "type": "tool_use", "id": "tu_1", "name": "submit_mission",
                                         "input": { "content": "지금 컵 찍어 와", "category": "일상",
                                                    "params": { "subject": "컵" } } } ] }
                        """, MediaType.APPLICATION_JSON));

        Map<String, Object> input = client.callTool("시스템 프롬프트", "입력 메시지", TOOL);

        assertEquals(Map.of("content", "지금 컵 찍어 와", "category", "일상", "params", Map.of("subject", "컵")), input);
        server.verify();
    }

    @Test
    void failuresBecomeShortReasonsWithoutKeyOrBody() {
        assertFails(withStatus(HttpStatusCode.valueOf(529)).body("{\"error\":\"overloaded secret-body " + KEY + "\"}"),
                "HTTP 529");
        assertFails(withUnauthorizedRequest().body("invalid x-api-key " + KEY), "HTTP 401");
        assertFails(withException(new SocketTimeoutException("read timed out")), "시간 초과");
        assertFails(withSuccess("이건 JSON 아님 secret-body", MediaType.TEXT_PLAIN), "JSON");
        assertFails(withSuccess("", MediaType.APPLICATION_JSON), "비어");
        assertFails(withSuccess("{\"stop_reason\":\"max_tokens\",\"content\":[]}", MediaType.APPLICATION_JSON), "잘림");
        assertFails(withSuccess("{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":\"secret-body\"}]}",
                MediaType.APPLICATION_JSON), "도구 결과가 없음");
        assertFails(withSuccess("{\"content\":[{\"type\":\"tool_use\",\"name\":\"other_tool\",\"input\":{}}]}",
                MediaType.APPLICATION_JSON), "도구 결과가 없음");
        assertFails(withSuccess("{\"content\":[{\"type\":\"tool_use\",\"name\":\"submit_mission\",\"input\":[1]}]}",
                MediaType.APPLICATION_JSON), "객체가 아님");
    }

    private void assertFails(ResponseCreator response, String reason) {
        server.reset();
        server.expect(requestTo("https://api.anthropic.com/v1/messages")).andRespond(response);
        AiGenerationException error = assertThrows(AiGenerationException.class,
                () -> client.callTool("시스템", "입력", TOOL));
        assertFalse(error.getMessage().contains(KEY), error.getMessage());
        assertFalse(error.getMessage().contains("secret-body"), error.getMessage());
        assertTrue(error.getMessage().contains(reason), error.getMessage());
        assertNull(error.getCause());  // 원래 예외(응답 본문이 들어 있을 수 있음)를 붙이지 않는다
    }
}
