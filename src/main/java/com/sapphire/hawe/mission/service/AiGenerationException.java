package com.sapphire.hawe.mission.service;

/** AI 로 미션을 만들지 못함. 메시지는 사유만 담는다 — API 키·응답 본문·AI 가 쓴 문장은 넣지 않는다. */
public class AiGenerationException extends RuntimeException {

    public AiGenerationException(String reason) {
        super(reason);
    }
}
