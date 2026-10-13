package com.sapphire.hawe.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 주제 vs 랜덤 주제 비교 실험(B9). 기본은 꺼짐 — 켜도 AI 를 쓸 수 없으면(키 없음 등) 군을 정하지 않는다.
 *
 * @param aiRatio AI 군에 들어갈 (그룹, 날짜) 비율 0~1
 * @param salt    배정 해시에 섞는 값. 바꾸면 배정이 새로 섞인다 (실험을 새로 시작할 때만 바꾼다)
 */
@ConfigurationProperties(prefix = "hawe.experiment.topic")
public record ExperimentProperties(boolean enabled, Double aiRatio, String salt) {

    public ExperimentProperties {
        aiRatio = aiRatio == null ? 0.5 : aiRatio;
        salt = salt == null || salt.isBlank() ? "hawe-topic-1" : salt.strip();
        if (aiRatio < 0 || aiRatio > 1)
            throw new IllegalArgumentException("hawe.experiment.topic.ai-ratio 는 0~1 이어야 함");
    }
}
