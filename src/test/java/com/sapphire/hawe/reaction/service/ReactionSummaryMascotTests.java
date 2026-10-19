package com.sapphire.hawe.reaction.service;

import com.sapphire.hawe.reaction.dao.ReactionRow;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReactionSummaryMascotTests {
    @Test void countIncludesMascotButUserIdsDoNot() {
        ReactionRow person = new ReactionRow();
        person.setUserId(1L); person.setStickerType("heart");
        ReactionRow mascot = new ReactionRow();
        mascot.setUserId(7L); mascot.setStickerType("heart"); mascot.setAi(true);
        var heart = ReactionService.summarize(List.of(person, mascot), 1L).getFirst();
        assertEquals(2, heart.count());
        assertEquals(List.of(1L), heart.userIds());
        assertTrue(heart.reactedByMe());
        assertTrue(heart.aiReacted());
    }
}
