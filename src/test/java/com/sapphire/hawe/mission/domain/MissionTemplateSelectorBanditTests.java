package com.sapphire.hawe.mission.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 선택기의 밴딧 입구(TODO_2/D4). 표본기가 없을 때 예전과 똑같은지, 있을 때 규칙 1~3 을 지키는지. */
class MissionTemplateSelectorBanditTests {
    private static final List<MissionTemplate> POOL = MissionTemplateSelector.MVP_POOL;

    /** 받은 후보를 기록하고 마지막 후보를 고르는 가짜 표본기 */
    private static final class Recording implements TemplateSampler {
        final List<List<MissionTemplate>> seen = new ArrayList<>();
        @Override public TemplateChoice choose(List<MissionTemplate> candidates, long seed) {
            seen.add(candidates);
            return new TemplateChoice(candidates.getLast(), 0.3);
        }
    }

    @Test void withoutSamplerItIsExactlyTheOldRandomChoice() {
        Random random = new Random(1);
        for (int i = 0; i < 1000; i++) {
            long group = random.nextInt(100_000);
            LocalDate date = LocalDate.of(2026, 1, 1).plusDays(random.nextInt(700));
            int daily = 1 + random.nextInt(5);
            int slot = 1 + random.nextInt(daily);
            Set<String> yesterday = random.nextBoolean() ? Set.of() : Set.of(List.of("PHOTO", "TEXT", "DRAW").get(random.nextInt(3)));
            TemplateChoice choice = MissionTemplateSelector.choose(group, date, slot, daily, yesterday, POOL, null);
            assertEquals(MissionTemplateSelector.select(group, date, slot, daily, yesterday, POOL), choice.template());
            long sameMedia = POOL.stream().filter(t -> t.media().equals(choice.template().media())).count();
            assertEquals(1.0 / sameMedia, choice.probability(), 1e-12);  // 무작위면 1/후보 수(그 매체의 틀 수)
        }
    }

    @Test void oneMissionADayBanditSeesAllMediaExceptYesterdays() {
        Recording sampler = new Recording();
        TemplateChoice choice = MissionTemplateSelector.choose(5, LocalDate.of(2026, 11, 24), 1, 1, Set.of("PHOTO"), POOL, sampler);
        Set<String> media = sampler.seen.getFirst().stream().map(MissionTemplate::media).collect(Collectors.toSet());
        assertEquals(Set.of("TEXT", "DRAW"), media);  // 어제 매체 제외, 나머지 매체의 틀 전체
        assertEquals(3, sampler.seen.getFirst().size());
        assertEquals(0.3, choice.probability());
        // 어제 모든 매체를 썼으면(하루 여러 개였다가 줄인 그룹) 풀 전체
        Recording all = new Recording();
        MissionTemplateSelector.choose(5, LocalDate.of(2026, 11, 24), 1, 1, Set.of("PHOTO", "TEXT", "DRAW"), POOL, all);
        assertEquals(EnumSet.copyOf(POOL), EnumSet.copyOf(all.seen.getFirst()));
    }

    @Test void severalMissionsADayKeepTheSlotMediaRotation() {
        LocalDate date = LocalDate.of(2026, 11, 24);
        for (int slot = 1; slot <= 3; slot++) {
            Recording sampler = new Recording();
            TemplateChoice choice = MissionTemplateSelector.choose(9, date, slot, 3, Set.of(), POOL, sampler);
            MissionTemplate random = MissionTemplateSelector.select(9, date, slot, 3, Set.of(), POOL);
            assertEquals(random.media(), choice.template().media(), "슬롯이 정한 매체는 그대로");
            if (!sampler.seen.isEmpty())
                assertTrue(sampler.seen.getFirst().stream().allMatch(t -> t.media().equals(random.media())));
        }
    }

    @Test void sameMediaAgainInADayUsesTheNextTemplate() {
        List<MissionTemplate> pool = List.of(MissionTemplate.PHOTO_NOW, MissionTemplate.PHOTO_GALLERY, MissionTemplate.PHOTO_COLOR,
                MissionTemplate.TEXT_ACROSTIC, MissionTemplate.TEXT_FILL_BLANK);  // 매체 2개
        LocalDate date = LocalDate.of(2026, 11, 24);
        Recording sampler = new Recording();
        TemplateChoice first = MissionTemplateSelector.choose(3, date, 1, 3, Set.of(), pool, sampler);
        TemplateChoice third = MissionTemplateSelector.choose(3, date, 3, 3, Set.of(), pool, sampler);  // 같은 매체 두 번째
        assertEquals(first.template().media(), third.template().media());
        List<MissionTemplate> inMedia = pool.stream().filter(t -> t.media().equals(first.template().media())).toList();
        assertEquals(inMedia.get((inMedia.indexOf(first.template()) + 1) % inMedia.size()), third.template());
        assertNotEquals(first.template(), third.template());
    }

    @Test void singleCandidateSkipsSamplingAndBadSamplersAreRejected() {
        TemplateSampler never = (candidates, seed) -> { throw new AssertionError("후보가 하나면 부르지 않아요"); };
        TemplateChoice only = MissionTemplateSelector.choose(1, LocalDate.of(2026, 11, 24), 1, 1, Set.of(),
                List.of(MissionTemplate.DRAW_A_TO_B), never);
        assertEquals(new TemplateChoice(MissionTemplate.DRAW_A_TO_B, 1.0), only);
        TemplateSampler outside = (candidates, seed) -> new TemplateChoice(MissionTemplate.TEXT_BALANCE, 0.5);
        assertThrows(IllegalStateException.class,
                () -> MissionTemplateSelector.choose(1, LocalDate.of(2026, 11, 24), 1, 1, Set.of(), POOL, outside));
    }

    @Test void choiceProbabilityMustBeValid() {
        assertThrows(IllegalArgumentException.class, () -> new TemplateChoice(MissionTemplate.PHOTO_NOW, 0));
        assertThrows(IllegalArgumentException.class, () -> new TemplateChoice(null, 0.5));
    }
}
