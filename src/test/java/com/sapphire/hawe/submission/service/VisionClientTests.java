package com.sapphire.hawe.submission.service;

import com.sapphire.hawe.global.config.VisionProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
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

    @Test void omitsApiKeyHeaderByDefault() {
        server.expect(requestTo("http://localhost:8001/v1/topic-fit"))
                .andExpect(headerDoesNotExist(VisionClient.API_KEY_HEADER))
                .andRespond(withSuccess("{\"fit\":0.19,\"model\":\"clip\",\"version\":\"1\"}", MediaType.APPLICATION_JSON));
        client.call("PHOTO_NOW", "topic", new byte[] {1}, "photo.jpg");
        server.verify();
    }

    @Test void sendsImageKeyAndApiKeyHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer keyServer = MockRestServiceServer.bindTo(builder).build();
        VisionClient keyed = new VisionClient(new VisionProperties(true, "https://vision.example", null, null, null,
                null, null, "secret", true), new ObjectMapper(), builder.build());
        keyServer.expect(requestTo("https://vision.example/v1/topic-fit"))
                .andExpect(header(VisionClient.API_KEY_HEADER, "secret"))
                .andExpect(request -> {
                    String body = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString();
                    assertTrue(body.contains("name=\"imageKey\""));
                    assertTrue(body.contains("submissions/photo.jpg"));
                    assertFalse(body.contains("name=\"image\";"));
                    assertTrue(body.contains("PHOTO_COLOR"));
                })
                .andRespond(withSuccess("{\"fit\":0.3,\"model\":\"clip\",\"version\":\"1\"}", MediaType.APPLICATION_JSON));
        assertEquals(0.3, keyed.callWithKey("PHOTO_COLOR", "빨강", "submissions/photo.jpg").fit());
        keyServer.verify();
    }

    @Test void invokesLambdaWithJsonPayload() {
        LambdaClient lambda = mock(LambdaClient.class);
        when(lambda.invoke(any(InvokeRequest.class))).thenReturn(InvokeResponse.builder().statusCode(200)
                .payload(SdkBytes.fromUtf8String("{\"fit\":0.42,\"model\":\"clip\",\"version\":\"1-fp16\"}")).build());
        VisionClient viaLambda = new VisionClient(lambdaProperties(), new ObjectMapper(), RestClient.create(), lambda);

        TopicFitResult result = viaLambda.callWithKey("PHOTO_NOW", "창밖 풍경", "submissions/photo.jpg");

        assertEquals(0.42, result.fit());
        ArgumentCaptor<InvokeRequest> request = ArgumentCaptor.forClass(InvokeRequest.class);
        verify(lambda).invoke(request.capture());
        assertEquals("hawe-vision", request.getValue().functionName());
        String payload = request.getValue().payload().asUtf8String();
        assertTrue(payload.contains("\"imageKey\":\"submissions/photo.jpg\""));
        assertTrue(payload.contains("창밖 풍경") && payload.contains("PHOTO_NOW"));
    }

    @Test void lambdaFunctionErrorAndErrorBodyBecomeVisionException() {
        LambdaClient lambda = mock(LambdaClient.class);
        VisionClient viaLambda = new VisionClient(lambdaProperties(), new ObjectMapper(), RestClient.create(), lambda);
        when(lambda.invoke(any(InvokeRequest.class))).thenReturn(InvokeResponse.builder().statusCode(200)
                .functionError("Unhandled").payload(SdkBytes.fromUtf8String("{}")).build());
        assertThrows(VisionException.class, () -> viaLambda.callWithKey("PHOTO_NOW", "topic", "submissions/photo.jpg"));
        when(lambda.invoke(any(InvokeRequest.class))).thenReturn(InvokeResponse.builder().statusCode(200)
                .payload(SdkBytes.fromUtf8String("{\"code\":\"IMAGE_NOT_FOUND\",\"message\":\"x\"}")).build());
        VisionException error = assertThrows(VisionException.class,
                () -> viaLambda.callWithKey("PHOTO_NOW", "topic", "submissions/photo.jpg"));
        assertTrue(error.getMessage().contains("IMAGE_NOT_FOUND"));
        when(lambda.invoke(any(InvokeRequest.class))).thenThrow(SdkClientException.create("timeout"));
        assertThrows(VisionException.class, () -> viaLambda.callWithKey("PHOTO_NOW", "topic", "submissions/photo.jpg"));
    }

    private static VisionProperties lambdaProperties() {
        return new VisionProperties(true, null, null, null, null, null, null, null, false, "hawe-vision", null);
    }

    @Test void realClientStaysOnHttp11() throws Exception {
        // 실제 JDK 클라이언트로 보낸다. h2c 업그레이드 헤더가 붙으면 uvicorn 이 400 으로 거절한다.
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> upgrade = new AtomicReference<>("not called");
        http.createContext("/v1/topic-fit", exchange -> {
            upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"fit\":0.2,\"model\":\"clip\",\"version\":\"1\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        http.start();
        try {
            VisionClient real = new VisionClient(new VisionProperties(true,
                    "http://127.0.0.1:" + http.getAddress().getPort(), Duration.ofSeconds(2), Duration.ofSeconds(5),
                    null, null, null), new ObjectMapper());
            assertEquals(0.2, real.call("PHOTO_NOW", "창밖 풍경", new byte[] {1, 2}, "photo.jpg").fit());
            assertNull(upgrade.get());
        } finally {
            http.stop(0);
        }
    }

    @Test void rejectsInvalidResponse() {
        server.expect(requestTo("http://localhost:8001/v1/topic-fit"))
                .andRespond(withSuccess("{\"fit\":2}", MediaType.APPLICATION_JSON));
        assertThrows(VisionException.class, () -> client.call("PHOTO_NOW", "topic", new byte[] {1}, "photo.jpg"));
    }
}
