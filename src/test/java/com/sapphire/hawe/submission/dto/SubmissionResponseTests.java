package com.sapphire.hawe.submission.dto;

import com.sapphire.hawe.group.dto.MemberProfileResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class SubmissionResponseTests {
    @Test void lateKeyAndOptionalFields() {
        var response = new SubmissionResponse(1L, 2L,
                new MemberProfileResponse(3L, "Writer", null, null), "TEXT_FILL_BLANK",
                null, null, null, "hello", null, null, null, List.of(), 0,
                OffsetDateTime.parse("2026-10-12T12:00:00+09:00"), true);
        String body = new ObjectMapper().writeValueAsString(response);
        assertTrue(body.contains("\"isLate\":true"));
        assertFalse(body.contains("\"late\":"));
        assertFalse(body.contains("\"mediaUrl\":"));
        assertTrue(body.contains("\"reactions\":[]"));
        assertFalse(body.contains("\"duration\""));
        assertFalse(body.contains("\"waveform\""));
        // 새 틀의 값은 그 틀에서만 나온다
        assertFalse(body.contains("\"number\"") || body.contains("\"choice\"") || body.contains("\"guess\""));
    }

    @Test void scalarAnswersAppearWhenPresent() {
        var response = new SubmissionResponse(1L, 2L, new MemberProfileResponse(3L, "Writer", null, null),
                "SLIDER_CONSENSUS", null, null, null, null, 70, null, 55, List.of(), 0,
                OffsetDateTime.parse("2026-10-13T12:00:00+09:00"), false);
        String body = new ObjectMapper().writeValueAsString(response);
        assertTrue(body.contains("\"number\":70"));
        assertTrue(body.contains("\"guess\":55"));
        assertFalse(body.contains("\"choice\""));
        assertFalse(body.contains("\"text\""));
    }
}
