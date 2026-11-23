package com.sapphire.hawe.mission.domain;

import java.util.List;

/**
 * 후보 틀 중 하나를 고르는 방법(TODO_2/D4). {@link MissionTemplateSelector} 가 후보(규칙 1~3)를 정한 뒤 "무작위 선택" 자리에서 부른다.
 * 같은 (후보, 시드, 통계)면 같은 결과여야 한다 — 현재 시각·전역 난수 금지.
 */
@FunctionalInterface
public interface TemplateSampler {
    /** [candidates] 는 2개 이상. 반환 틀은 반드시 후보 중 하나. */
    TemplateChoice choose(List<MissionTemplate> candidates, long seed);
}
