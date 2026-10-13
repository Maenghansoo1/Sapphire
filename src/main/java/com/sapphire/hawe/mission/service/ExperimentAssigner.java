package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.config.ExperimentProperties;
import com.sapphire.hawe.mission.domain.ExperimentArm;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Optional;

/**
 * (그룹, 날짜)를 실험 군에 결정적으로 나눈다 — 같은 입력은 언제나 같은 군. 틀·대상 선택과는 무관하다.
 * 실험이 꺼져 있거나 AI 를 쓸 수 없으면 군을 정하지 않는다(기존 발행 그대로).
 */
@Component
@EnableConfigurationProperties(ExperimentProperties.class)
public class ExperimentAssigner {

    private final ExperimentProperties properties;
    private final AiMissionGenerator ai;

    public ExperimentAssigner(ExperimentProperties properties, AiMissionGenerator ai) {
        this.properties = properties;
        this.ai = ai;
    }

    public Optional<ExperimentArm> assign(long groupId, LocalDate date) {
        if (!properties.enabled() || !ai.available()) return Optional.empty();
        return Optional.of(bucket(properties.salt(), groupId, date) < properties.aiRatio() ? ExperimentArm.AI : ExperimentArm.RANDOM);
    }

    /** SHA-256(salt|그룹|날짜) 앞 8바이트를 0 이상 1 미만으로. */
    static double bucket(String salt, long groupId, LocalDate date) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((salt + "|" + groupId + "|" + date).getBytes(StandardCharsets.UTF_8));
            return (ByteBuffer.wrap(hash).getLong() >>> 11) * 0x1.0p-53;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);  // 모든 JVM 에 있는 알고리즘
        }
    }
}
