package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.config.MissionAiProperties;
import com.sapphire.hawe.mission.domain.GeneratedMission;
import com.sapphire.hawe.mission.domain.GeneratedMissionValidator;
import com.sapphire.hawe.mission.domain.GenerationContext;
import com.sapphire.hawe.mission.domain.GroupSignals;
import com.sapphire.hawe.mission.domain.MissionTemplate;
import com.sapphire.hawe.mission.domain.RecentMission;
import com.sapphire.hawe.mission.domain.SpecialDay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * 그룹 정보를 넣어 AI 에게 미션 문장·빈칸을 받고 서버가 다시 검사한다. 틀·대상은 서버가 이미 정했다.
 * AI 로 보내는 것은 날짜·관계·틀·최근 미션 문장·기념일·미션 단위 숫자뿐이다 — 이름·그룹 이름·답·댓글은 보내지 않는다.
 * 실패하면 {@link AiGenerationException} (고정 문장으로 바꾸는 일은 {@link FallbackMissionGenerator}).
 */
@Component
@EnableConfigurationProperties(MissionAiProperties.class)
public class AiMissionGenerator {

    static final String TOOL_NAME = "submit_mission";
    static final String DEFAULT_PROMPT = "classpath:mission-ai/default-prompt.txt";
    static final String SERVER_GUIDE = "classpath:mission-ai/server-guide.txt";
    static final Map<String, String> RELATIONS = Map.of("COUPLE", "연인", "FAMILY", "가족", "FRIEND", "친구",
            "RIVAL", "웬수", "CASUAL", "그저그런사이");
    static final Map<String, String> MEDIA = Map.of("PHOTO", "사진", "TEXT", "글", "DRAW", "그림", "SLIDER", "슬라이더");
    // 도구 정의는 바뀌지 않게 둔다 (시스템 프롬프트와 함께 캐시된다). 틀별 빈칸은 입력 메시지에 적는다
    static final Map<String, Object> TOOL = linked(
            "name", TOOL_NAME,
            "description", "만든 미션 하나를 서버에 돌려준다.",
            "input_schema", linked(
                    "type", "object",
                    "properties", linked(
                            "content", linked("type", "string", "description", "멤버들에게 보여줄 미션 문장 한 줄"),
                            "category", linked("type", "string", "description", "카테고리 목록 중 하나 그대로"),
                            "params", linked("type", "object", "description", "틀의 빈칸 이름과 값. 빈칸이 없는 틀이면 비워 둔다",
                                    "additionalProperties", linked("type", List.of("string", "integer")))),
                    "required", List.of("content", "category", "params"),
                    "additionalProperties", false));
    private static final Logger log = LoggerFactory.getLogger(AiMissionGenerator.class);

    private final MissionAiProperties properties;
    private final AnthropicClient client;
    private final ObjectMapper objectMapper;
    private final LongSupplier clock;
    private final String systemPrompt;

    @Autowired
    public AiMissionGenerator(MissionAiProperties properties, AnthropicClient client, ObjectMapper objectMapper,
            ResourceLoader resources) {
        this(properties, client, objectMapper, resources, System::nanoTime);
    }

    AiMissionGenerator(MissionAiProperties properties, AnthropicClient client, ObjectMapper objectMapper,
            ResourceLoader resources, LongSupplier clock) {
        this.properties = properties;
        this.client = client;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.systemPrompt = loadPrompt(properties.promptPaths(), resources);
    }

    public boolean available() {
        return properties.usable();
    }

    public GeneratedMission generate(MissionTemplate template, GenerationContext context, boolean withTarget) {
        String input = input(template, context, withTarget);
        long started = clock.getAsLong();
        String reason = "시도하지 않음";
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            // 발행 트랜잭션 안에서 기다리므로, 다음 시도가 총 제한을 넘길 수 있으면 멈춘다
            Duration spent = Duration.ofNanos(clock.getAsLong() - started);
            if (attempt > 1 && spent.plus(properties.readTimeout()).compareTo(properties.totalTimeout()) > 0) break;
            try {
                Map<String, Object> raw = client.callTool(systemPrompt, input, TOOL);
                return GeneratedMissionValidator.validate(raw, template, withTarget, context.recentContents(),
                        properties.categories(), properties.blockedWords());
            } catch (GeneratedMissionValidator.InvalidMission e) {
                reason = "검증 실패(" + e.getMessage() + ")";
            } catch (AiGenerationException e) {
                reason = e.getMessage();
            }
            log.debug("AI mission attempt {} for {} failed: {}", attempt, template, reason);
        }
        throw new AiGenerationException(reason);
    }

    String systemPrompt() {
        return systemPrompt;
    }

    /** AI 에게 보내는 입력. 사람·그룹을 알아볼 수 있는 값은 넣지 않는다. */
    String input(MissionTemplate template, GenerationContext context, boolean withTarget) {
        Map<String, Object> blanks = new LinkedHashMap<>(template.paramsSchema());
        blanks.remove("targetUserId");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("오늘", context.date() + " " + context.date().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN));
        input.put("그룹 관계", RELATIONS.getOrDefault(context.relationType(), "그저그런사이"));
        input.put("틀", linked("코드", template.name(), "이름", template.displayName(), "빈칸", blanks));
        input.put("대상", withTarget ? "대상 있음" : "대상 없음");
        List<Map<String, Object>> recent = new ArrayList<>();
        for (RecentMission mission : context.recentMissions()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("문장", mission.content());
            if (mission.category() != null) item.put("카테고리", mission.category());
            item.put("틀", templateName(mission.template()));
            recent.add(item);
        }
        input.put("최근 미션", recent);
        input.put("카테고리 목록", properties.categories());
        input.put("특별한 날", context.specialDays().stream().map(AiMissionGenerator::specialDay).toList());
        input.put("그룹 기록", signals(context.signals()));
        return "지금 만들 미션의 정보야. 결과는 " + TOOL_NAME + " 도구로 돌려줘.\n"
                + objectMapper.writeValueAsString(input);
    }

    private static Map<String, Object> specialDay(SpecialDay day) {
        return linked("이름", day.name(), "남은 날", day.daysAway());
    }

    /** 넣은 순서를 지키는 맵 (JSON 키 순서가 실행마다 바뀌지 않게). */
    private static Map<String, Object> linked(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) map.put((String) keyValues[i], keyValues[i + 1]);
        return map;
    }

    private static Map<String, Object> signals(GroupSignals signals) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (signals == null) return result;
        result.put("멤버 수", signals.memberCount());
        result.put("함께한 날", signals.daysTogether());
        result.put("최근 미션 참여", signals.recentMissions().stream().map(stat -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("틀", templateName(stat.template()));
            item.put("문장", stat.content());
            item.put("참여율", stat.participationRate());
            item.put("제출당 반응", stat.engagementPerSubmission());
            return item;
        }).toList());
        Map<String, Object> media = new LinkedHashMap<>();
        signals.mediaParticipation().forEach((key, rate) -> media.put(MEDIA.getOrDefault(key, key), rate));
        result.put("매체별 참여율", media);
        return result;
    }

    private static String templateName(String code) {
        try {
            return MissionTemplate.valueOf(code).displayName();
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return code;  // 지금은 없는 옛 틀(음성 등)
        }
    }

    /** 설정한 프롬프트 파일들을 이어 붙이고(없으면 임시 기본 프롬프트) 서버 안내를 덧붙인다. */
    private static String loadPrompt(List<String> paths, ResourceLoader resources) {
        List<String> parts = new ArrayList<>();
        List<String> loaded = new ArrayList<>();
        for (String path : paths) {
            String text = read(resources.getResource(path));
            if (text == null) {
                log.warn("Mission prompt not found: {}", path);
            } else {
                parts.add(text.strip());
                loaded.add(path);
            }
        }
        if (parts.isEmpty()) {
            String fallback = read(resources.getResource(DEFAULT_PROMPT));
            if (fallback == null) throw new IllegalStateException("Default mission prompt is missing: " + DEFAULT_PROMPT);
            parts.add(fallback.strip());
            loaded.add(DEFAULT_PROMPT);
        }
        String guide = read(resources.getResource(SERVER_GUIDE));
        if (guide == null) throw new IllegalStateException("Mission server guide is missing: " + SERVER_GUIDE);
        parts.add(guide.strip());
        log.info("Mission AI prompt: {} + server guide", loaded);
        return String.join("\n\n---\n\n", parts);
    }

    private static String read(Resource resource) {
        if (!resource.exists()) return null;
        try (var stream = resource.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
