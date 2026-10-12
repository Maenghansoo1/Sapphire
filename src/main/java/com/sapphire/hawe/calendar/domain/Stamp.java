package com.sapphire.hawe.calendar.domain;

/**
 * 달력에 찍히는 내 도장. DB에 저장하지 않고 볼 때마다 제출 기록으로 계산한다.
 */
public enum Stamp {
    GOOD,
    LATE,
    PENALTY;

    /**
     * @param submitted           이 미션에 냈는지
     * @param late                마감 뒤에 냈는지 (냈을 때만 의미 있음)
     * @param closed              다음 미션이 열려서 더 이상 낼 수 없는지
     * @param memberWhenPublished 미션이 열릴 때 이미 그룹에 있었는지
     * @return 도장이 없으면 null
     */
    public static Stamp of(boolean submitted, boolean late, boolean closed, boolean memberWhenPublished) {
        if (submitted) {
            return late ? LATE : GOOD;
        }
        if (closed && memberWhenPublished) {
            return PENALTY;
        }
        return null;
    }
}
