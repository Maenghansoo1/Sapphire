package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.mission.domain.GeneratedMission;
import com.sapphire.hawe.mission.domain.MissionTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
public class PlaceholderMissionGenerator implements MissionGenerator {

    @Override
    public GeneratedMission generate(MissionTemplate template, long seed, String previousContent, boolean withTarget) {
        List<GeneratedMission> variants = variants(template, withTarget);
        int index = new Random(seed).nextInt(variants.size());
        for (int offset = 0; offset < variants.size(); offset++) {
            GeneratedMission candidate = variants.get((index + offset) % variants.size());
            if (!candidate.content().equals(previousContent)) return candidate;
        }
        throw new IllegalStateException("No distinct placeholder mission");
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
            default -> throw new IllegalArgumentException("Unsupported placeholder template: " + template);
        };
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
