package com.sapphire.hawe.global.config;

import com.sapphire.hawe.mission.domain.MissionTemplate;
import com.sapphire.hawe.mission.domain.MissionTemplateSelector;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalTime;
import java.util.List;

/**
 * {@code enabledTemplates}: 발행 풀(실제로 고를 수 있는 틀). 설정하지 않으면 MVP 7종, 빈 목록이면 시작 시 오류.
 * 새 틀은 앱이 그릴 수 있게 된 뒤에 켠다(구 앱이 모르는 틀을 받으면 미션을 열 수 없음).
 */
@ConfigurationProperties(prefix = "hawe.mission")
public record MissionProperties(LocalTime defaultPublishTime, LocalTime defaultDeadlineTime, int maxDailyCount,
        List<MissionTemplate> enabledTemplates) {

    public MissionProperties {
        enabledTemplates = enabledTemplates == null ? MissionTemplateSelector.MVP_POOL : List.copyOf(enabledTemplates);
        if (enabledTemplates.isEmpty())
            throw new IllegalArgumentException("hawe.mission.enabled-templates 에 틀이 하나도 없습니다");
        for (MissionTemplate template : enabledTemplates)
            if (template.phase() != 1)
                throw new IllegalArgumentException("1차 틀만 발행할 수 있습니다: " + template);
    }
}
