package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.mission.domain.GeneratedMission;
import com.sapphire.hawe.mission.domain.GenerationContext;
import com.sapphire.hawe.mission.domain.MissionTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
public class PlaceholderMissionGenerator implements MissionGenerator {

    /** 최근에 쓰지 않은 변형을 시드 순서대로 고르고, 모두 썼으면 가장 오래전에 쓴 변형을 고른다. 다른 그룹 정보는 쓰지 않는다. */
    @Override
    public GeneratedMission generate(MissionTemplate template, long seed, GenerationContext context, boolean withTarget) {
        List<String> recent = context == null ? List.of() : context.recentContents();
        List<GeneratedMission> variants = variants(template, withTarget);
        int index = new Random(seed).nextInt(variants.size());
        GeneratedMission leastRecent = null;
        int leastRecentAt = -1;
        for (int offset = 0; offset < variants.size(); offset++) {
            GeneratedMission candidate = variants.get((index + offset) % variants.size());
            int usedAt = recent.indexOf(candidate.content());
            if (usedAt < 0) return candidate;
            if (usedAt > leastRecentAt) {
                leastRecent = candidate;
                leastRecentAt = usedAt;
            }
        }
        return leastRecent;
    }

    private List<GeneratedMission> variants(MissionTemplate template, boolean withTarget) {
        return switch (template) {
            case PHOTO_NOW -> List.of(
                    new GeneratedMission("지금 창밖으로 보이는 풍경을 찍어줘", "일상", Map.of("subject", "창밖 풍경")),
                    new GeneratedMission("지금 눈앞의 작은 물건을 찍어줘", "일상", Map.of("subject", "눈앞의 작은 물건")),
                    new GeneratedMission("오늘 가장 마음에 드는 장면을 찍어줘", "감성", Map.of("subject", "마음에 드는 장면")));
            case PHOTO_GALLERY -> List.of(
                    new GeneratedMission("갤러리에서 가장 오래된 사진 하나를 보여줘", "추억", Map.of("condition", "가장 오래된 사진")),
                    new GeneratedMission("갤러리에서 가장 최근 웃은 사진을 골라줘", "추억", Map.of("condition", "최근 웃은 사진")),
                    new GeneratedMission("갤러리에서 색이 예쁜 사진을 골라줘", "감성", Map.of("condition", "색이 예쁜 사진")));
            case PHOTO_COLOR -> List.of(
                    new GeneratedMission("주변에서 파란색인 것을 찍어줘", "일상", Map.of("color", "파란색")),
                    new GeneratedMission("주변에서 노란색인 것을 찍어줘", "일상", Map.of("color", "노란색")),
                    new GeneratedMission("주변에서 초록색인 것을 찍어줘", "일상", Map.of("color", "초록색")));
            case TEXT_ACROSTIC -> List.of(
                    acrostic("퇴근길"), acrostic("바람결"), acrostic("별자리"));
            case TEXT_FILL_BLANK -> List.of(
                    blank("[대상]의 하루를 색으로 표현하면 ___일 것 같아"),
                    blank("[대상]에게 오늘 어울리는 별명은 ___이야"),
                    blank("[대상]와 같이 가고 싶은 곳은 ___이야"));
            case DRAW_SKETCH -> List.of(
                    sketch(withTarget, "얼굴"), sketch(withTarget, "표정"), sketch(withTarget, "머리 모양"));
            case DRAW_A_TO_B -> List.of(
                    drawTo(withTarget, "구름", "풍선"), drawTo(withTarget, "컵", "화분"),
                    drawTo(withTarget, "책", "집"));
            case TEXT_ONE_WORD -> List.of(
                    new GeneratedMission("오늘 하루를 한 단어로 표현해줘", "일상", Map.of()),
                    new GeneratedMission("지금 기분을 한 단어로 말해줘", "감성", Map.of()),
                    new GeneratedMission("이번 주를 한 단어로 정리해줘", "일상", Map.of()));
            case TEXT_REVIEW -> List.of(
                    new GeneratedMission("오늘 먹은 것 하나에 별점과 한 줄 후기를 남겨줘", "일상", Map.of()),
                    new GeneratedMission("오늘 하루에 별점을 준다면? 한 줄 후기도 남겨줘", "감성", Map.of()),
                    new GeneratedMission("최근에 본 영상이나 들은 노래에 별점과 한 줄 후기를 남겨줘", "일상", Map.of()));
            case TEXT_BALANCE -> List.of(
                    balance("평생 여름", "평생 겨울"), balance("짜장면", "짬뽕"), balance("산", "바다"));
            case SLIDER_POSITION -> List.of(
                    position("나는 계획형일까 즉흥형일까?", "계획형", "즉흥형"),
                    position("요즘 나의 에너지는 어느 정도?", "방전", "충전 완료"),
                    position("주말의 나는 어느 쪽?", "집콕", "밖으로"));
            case SLIDER_TASTE -> List.of(
                    taste("민트초코", "별로", "최고"), taste("매운 음식", "못 먹어", "잘 먹어"),
                    taste("공포 영화", "별로", "최고"));
            case SLIDER_CONSENSUS -> List.of(
                    consensus("우리 그룹은 아침형일까 저녁형일까?", "아침형", "저녁형"),
                    consensus("우리 그룹은 여행 갈 때 계획형일까 즉흥형일까?", "계획형", "즉흥형"),
                    consensus("우리 그룹은 답장이 빠른 편일까?", "느긋하게", "바로바로"));
            default -> throw new IllegalArgumentException("Unsupported placeholder template: " + template);
        };
    }

    private GeneratedMission balance(String optionA, String optionB) {
        return new GeneratedMission(optionA + " vs " + optionB + ", 하나만 고른다면?", "유머",
                Map.of("optionA", optionA, "optionB", optionB));
    }

    private GeneratedMission position(String question, String left, String right) {
        return new GeneratedMission(question, "일상", Map.of("question", question, "left", left, "right", right));
    }

    private GeneratedMission taste(String topic, String left, String right) {
        return new GeneratedMission(topic + ", 내 취향은 어디쯤?", "일상",
                Map.of("topic", topic, "left", left, "right", right));
    }

    private GeneratedMission consensus(String question, String left, String right) {
        return new GeneratedMission(question + " 내 위치를 고르고 그룹 평균도 맞혀봐", "유머",
                Map.of("question", question, "left", left, "right", right));
    }

    private GeneratedMission acrostic(String word) {
        return new GeneratedMission(word + "로 삼행시를 지어줘", "유머", Map.of("word", word));
    }

    private GeneratedMission blank(String sentence) {
        return new GeneratedMission(sentence, "유머", Map.of("sentence", sentence));
    }

    private GeneratedMission sketch(boolean withTarget, String subject) {
        return new GeneratedMission((withTarget ? "[대상]의 " : "오늘의 ") + subject + "을 30초 안에 그려줘",
                "유머", Map.of("subject", subject, "seconds", 30));
    }

    private GeneratedMission drawTo(boolean withTarget, String a, String b) {
        return new GeneratedMission((withTarget ? "[대상]을 " : a + "을 ") + b + "로 바꿔 그려줘",
                "유머", Map.of("a", a, "b", b));
    }
}
