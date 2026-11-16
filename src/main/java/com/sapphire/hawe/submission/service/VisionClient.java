package com.sapphire.hawe.submission.service;

import com.sapphire.hawe.global.config.VisionProperties;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@EnableConfigurationProperties(VisionProperties.class)
public class VisionClient {
    static final String API_KEY_HEADER = "X-Hawe-Vision-Key";

    private final VisionProperties properties;
    private final ObjectMapper json;
    private final RestClient rest;

    @Autowired
    public VisionClient(VisionProperties properties, ObjectMapper json) {
        this(properties, json, RestClient.builder().requestFactory(requestFactory(properties)).build());
    }

    VisionClient(VisionProperties properties, ObjectMapper json, RestClient rest) {
        this.properties = properties;
        this.json = json;
        this.rest = rest;
    }

    public TopicFitResult call(String template, String topic, byte[] image, String filename) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("image", new ByteArrayResource(image) {
            @Override public String getFilename() { return filename; }
        });
        return post(parts, template, topic);
    }

    /** 사진 바이트 대신 저장소 키를 보낸다. 비전 서비스가 S3 에서 직접 읽는다(Lambda 배포). */
    public TopicFitResult callWithKey(String template, String topic, String imageKey) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("imageKey", imageKey);
        return post(parts, template, topic);
    }

    private TopicFitResult post(MultiValueMap<String, Object> parts, String template, String topic) {
        parts.add("topic", topic);
        parts.add("template", template);
        String body;
        try {
            body = rest.post().uri(properties.baseUrl() + "/v1/topic-fit")
                    .headers(headers -> {
                        if (!properties.apiKey().isEmpty()) headers.set(API_KEY_HEADER, properties.apiKey());
                    })
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(parts).retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            throw new VisionException("Vision HTTP " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new VisionException("Vision connection failed or timed out");
        } catch (RestClientException e) {
            throw new VisionException("Vision request failed");
        }
        try {
            JsonNode result = json.readTree(body);
            if (result == null || !result.isObject() || !result.path("fit").isNumber())
                throw new VisionException("Invalid vision response");
            double fit = result.path("fit").asDouble();
            if (!Double.isFinite(fit) || fit < 0 || fit > 1)
                throw new VisionException("Invalid vision fit");
            return new TopicFitResult(fit, result.path("model").asString(""), result.path("version").asString(""));
        } catch (VisionException e) {
            throw e;
        } catch (Exception e) {
            throw new VisionException("Invalid vision response");
        }
    }

    private static JdkClientHttpRequestFactory requestFactory(VisionProperties properties) {
        // uvicorn 은 JDK 클라이언트 기본값(HTTP/2)이 보내는 h2c 업그레이드 요청을 400 으로 거절해서 HTTP/1.1 로 고정한다.
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}
