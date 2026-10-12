package com.sapphire.hawe.calendar.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StampTests {

    @Test
    void submittedBeforeDeadlineIsGood() {
        assertEquals(Stamp.GOOD, Stamp.of(true, false, true, true));
    }

    @Test
    void submittedAfterDeadlineIsLate() {
        assertEquals(Stamp.LATE, Stamp.of(true, true, true, true));
    }

    @Test
    void submittedStampDoesNotDependOnJoinTime() {
        assertEquals(Stamp.GOOD, Stamp.of(true, false, false, false));
    }

    @Test
    void missedClosedMissionIsPenalty() {
        assertEquals(Stamp.PENALTY, Stamp.of(false, false, true, true));
    }

    @Test
    void missionThatCanStillBeSubmittedHasNoStamp() {
        assertNull(Stamp.of(false, false, false, true));
    }

    @Test
    void missionPublishedBeforeJoiningHasNoStamp() {
        assertNull(Stamp.of(false, false, true, false));
    }
}
