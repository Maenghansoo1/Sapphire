package com.sapphire.hawe.mission.domain;

/**
 * 고른 틀과 그 틀이 뽑힐 확률(0 초과 1 이하). 확률은 나중에 정책 효과를 역확률 가중(IPS)으로 비교할 때 쓴다(TODO_2/D4·D5).
 * 무작위 정책이면 1/후보 수, 밴딧이면 근사값, 후보가 하나면 1.
 */
public record TemplateChoice(MissionTemplate template, double probability) {
    public TemplateChoice {
        if (template == null || !(probability > 0 && probability <= 1 + 1e-9))
            throw new IllegalArgumentException("틀이 있고 확률은 0 초과 1 이하여야 함");
    }
}
