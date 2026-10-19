package com.sapphire.hawe.submission.service;

import com.sapphire.hawe.global.config.VisionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class VisionClientTests {
    MockRestServiceServer server;
    VisionClient client;

    @BeforeEach void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new VisionClient(new VisionProperties(true, null, null, null, null, null, null),
                new ObjectMapper(), builder.build());
    }

    @Test void sendsUtf8MultipartWithFilenameAndParsesFit() {
        server.expect(requestTo("http://localhost:8001/v1/topic-fit"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(request -> {
                    String body = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString();
                    assertTrue(body.contains("filename=\"photo.jpg\""));
                    assertTrue(body.contains("창밖 풍경"));
                    assertTrue(body.contains("PHOTO_NOW"));
                })
                .andRespond(withSuccess("{\"fit\":0.19,\"model\":\"clip\",\"version\":\"1\"}", MediaType.APPLICATION_JSON));
        assertEquals(0.19, client.call("PHOTO_NOW", "창밖 풍경", new byte[] {1, 2}, "photo.jpg").fit());
        server.verify();
    }

    @Test void rejectsInvalidResponse() {
        server.expect(requestTo("http://localhost:8001/v1/topic-fit"))
                .andRespond(withSuccess("{\"fit\":2}", MediaType.APPLICATION_JSON));
        assertThrows(VisionException.class, () -> client.call("PHOTO_NOW", "topic", new byte[] {1}, "photo.jpg"));
    }
}
