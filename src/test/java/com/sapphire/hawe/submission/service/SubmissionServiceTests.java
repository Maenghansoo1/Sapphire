package com.sapphire.hawe.submission.service;

import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.storage.FileStorage;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.mission.dao.MissionMapper;
import com.sapphire.hawe.mission.dao.MissionRow;
import com.sapphire.hawe.submission.dao.SubmissionInsertRow;
import com.sapphire.hawe.submission.dao.SubmissionMapper;
import com.sapphire.hawe.submission.dao.SubmissionRow;
import com.sapphire.hawe.reaction.dao.ReactionMapper;
import com.sapphire.hawe.comment.dao.CommentMapper;
import com.sapphire.hawe.comment.dao.CommentCountRow;
import com.sapphire.hawe.reaction.dao.ReactionRow;
import java.util.List;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubmissionServiceTests {
    MissionMapper missions = mock(MissionMapper.class);
    GroupMemberMapper members = mock(GroupMemberMapper.class);
    SubmissionMapper submissions = mock(SubmissionMapper.class);
    FileStorage storage = mock(FileStorage.class);
    ReactionMapper reactions = mock(ReactionMapper.class);
    CommentMapper comments = mock(CommentMapper.class);
    SubmissionService service = new SubmissionService(missions, members, submissions, storage,
            new ObjectMapper(), reactions, comments);
    MissionRow mission = new MissionRow();

    @BeforeEach void setUp() {
        mission.setId(1L); mission.setGroupId(2L); mission.setTemplate("TEXT_FILL_BLANK");
        mission.setDeadlineAt(LocalDateTime.now().plusDays(1));
        when(missions.findById(1L, 3L)).thenReturn(mission);
        when(members.existsByGroupIdAndUserId(2L, 3L)).thenReturn(true);
        when(missions.isCurrent(1L)).thenReturn(true);
    }

    private String error(MultiValueMap<String, String> fields, MultiValueMap<String, MultipartFile> files) {
        return assertThrows(BusinessException.class, () -> service.submit(3L, 1L, fields, files)).errorCode().code();
    }
    private static LinkedMultiValueMap<String, String> fields(String key, String value) {
        LinkedMultiValueMap<String, String> map = new LinkedMultiValueMap<>(); map.add(key, value); return map;
    }
    private static LinkedMultiValueMap<String, MultipartFile> files(byte[] bytes) {
        LinkedMultiValueMap<String, MultipartFile> map = new LinkedMultiValueMap<>();
        map.add("file", new MockMultipartFile("file", "claimed.jpg", "image/jpeg", bytes)); return map;
    }

    @Test void validationOrder() {
        when(missions.findById(1L, 3L)).thenReturn(null);
        assertEquals("MISSION_NOT_FOUND", error(fields("text", "hello"), files(new byte[]{1})));
        when(missions.findById(1L, 3L)).thenReturn(mission);
        when(members.existsByGroupIdAndUserId(2L, 3L)).thenReturn(false);
        assertEquals("NOT_GROUP_MEMBER", error(fields("text", "hello"), files(new byte[]{1})));
        when(members.existsByGroupIdAndUserId(2L, 3L)).thenReturn(true);
        when(missions.isCurrent(1L)).thenReturn(false);
        assertEquals("MISSION_CLOSED", error(fields("text", "hello"), files(new byte[]{1})));
        when(missions.isCurrent(1L)).thenReturn(true);
        when(submissions.existsByMissionIdAndUserId(1L, 3L)).thenReturn(true);
        assertEquals("ALREADY_SUBMITTED", error(fields("text", "hello"), files(new byte[]{1})));
        when(submissions.existsByMissionIdAndUserId(1L, 3L)).thenReturn(false);
        mission.setTemplate("SLIDER_POSITION");
        assertEquals("INVALID_SUBMISSION", error(fields("text", "hello"), new LinkedMultiValueMap<>()));
    }

    @Test void textAndAcrosticRules() {
        assertEquals("INVALID_SUBMISSION", error(fields("text", " "), new LinkedMultiValueMap<>()));
        assertEquals("INVALID_SUBMISSION", error(fields("text", "a".repeat(101)), new LinkedMultiValueMap<>()));
        assertEquals("INVALID_SUBMISSION", error(fields("number", "1"), new LinkedMultiValueMap<>()));
        assertEquals("INVALID_SUBMISSION", error(fields("text", "okay"), files(new byte[]{1})));
        mission.setTemplate("TEXT_ACROSTIC"); mission.setParams("{\"word\":\"가나다\"}");
        assertEquals("INVALID_SUBMISSION", error(fields("text", "가\n나"), new LinkedMultiValueMap<>()));
        assertEquals("INVALID_SUBMISSION", error(fields("text", "가\n나\n라"), new LinkedMultiValueMap<>()));
        assertEquals("INVALID_SUBMISSION", error(fields("text", "가\n \n다"), new LinkedMultiValueMap<>()));
        doThrow(new DuplicateKeyException("duplicate")).when(submissions).insert(any());
        assertEquals("ALREADY_SUBMITTED", error(fields("text", "가\r\n나\r\n다\n\n"), new LinkedMultiValueMap<>()));
        mission.setTemplate("TEXT_FILL_BLANK");
        assertEquals("ALREADY_SUBMITTED", error(fields("text", "😀".repeat(100)), new LinkedMultiValueMap<>()));
        assertEquals("INVALID_SUBMISSION", error(fields("text", "😀".repeat(101)), new LinkedMultiValueMap<>()));
    }

    @Test void imageRules() {
        mission.setTemplate("PHOTO_NOW");
        assertEquals("INVALID_SUBMISSION", error(new LinkedMultiValueMap<>(), new LinkedMultiValueMap<>()));
        assertEquals("UNSUPPORTED_FILE_TYPE", error(new LinkedMultiValueMap<>(), files("not an image".getBytes())));
        assertEquals("FILE_TOO_LARGE", error(new LinkedMultiValueMap<>(), files(new byte[10 * 1024 * 1024 + 1])));
        byte[] jpg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
        assertEquals("INVALID_SUBMISSION", error(fields("duration", "3"), files(jpg)));
        assertEquals("INVALID_SUBMISSION", error(fields("waveform", "[0,1]"), files(jpg)));
        doThrow(new DuplicateKeyException("duplicate")).when(submissions).insert(any());
        when(storage.store(eq("submissions"), any(), eq("jpg"))).thenReturn("submissions/test.jpg");
        assertEquals("ALREADY_SUBMITTED", error(fields("caption", "hi"), files(jpg)));
        verify(storage).delete("submissions/test.jpg");
    }

    @Test void audioFilesAndRemovedVoiceTemplatesAreRejected() {
        mission.setTemplate("PHOTO_NOW");
        for (byte[] audio : List.of("ID3payload".getBytes(), "xxxxftypM4A ".getBytes(),
                new byte[]{(byte) 0xff, (byte) 0xf1, 0}, new byte[]{0x1a, 0x45, (byte) 0xdf, (byte) 0xa3}))
            assertEquals("UNSUPPORTED_FILE_TYPE", error(new LinkedMultiValueMap<>(), files(audio)));
        mission.setTemplate("VOICE_LINE");
        assertEquals("INVALID_SUBMISSION", error(new LinkedMultiValueMap<>(), files("ID3payload".getBytes())));
        verify(storage, never()).store(any(), any(), any());
    }

    @Test void rollbackDeletesStoredFile() {
        mission.setTemplate("PHOTO_NOW");
        byte[] jpg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
        when(storage.store(eq("submissions"), any(), eq("jpg"))).thenReturn("submissions/stored.jpg");
        doAnswer(invocation -> { invocation.getArgument(0, com.sapphire.hawe.submission.dao.SubmissionInsertRow.class)
                .setId(99L); return null; }).when(submissions).insert(any());
        SubmissionRow row = new SubmissionRow();
        row.setId(99L); row.setMissionId(1L); row.setGroupId(2L); row.setUserId(3L);
        row.setTemplate("PHOTO_NOW"); row.setMediaUrl("submissions/stored.jpg");
        row.setSubmittedAt(LocalDateTime.now());
        when(submissions.findDetailById(99L)).thenReturn(row);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.submit(3L, 1L, new LinkedMultiValueMap<>(), files(jpg));
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(storage).delete("submissions/stored.jpg");
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test void feedBatchesReactionsAndCommentCounts() {
        when(submissions.existsByMissionIdAndUserId(1L, 3L)).thenReturn(true);
        SubmissionRow first = feedRow(10L);
        SubmissionRow second = feedRow(11L);
        when(submissions.findFeedByMissionId(1L, 0L, 20)).thenReturn(List.of(first, second));
        when(submissions.countByMissionId(1L)).thenReturn(2L);
        ReactionRow reaction = new ReactionRow();
        reaction.setSubmissionId(10L); reaction.setUserId(3L); reaction.setStickerType("heart");
        when(reactions.findBySubmissionIds(List.of(10L, 11L))).thenReturn(List.of(reaction));
        CommentCountRow count = new CommentCountRow();
        count.setSubmissionId(10L); count.setCount(2);
        when(comments.countBySubmissionIds(List.of(10L, 11L))).thenReturn(List.of(count));

        var page = service.feed(3L, 1L, 0, 20);

        assertEquals(2, page.content().size());
        assertEquals(2, page.content().getFirst().commentCount());
        assertTrue(page.content().getFirst().reactions().getFirst().reactedByMe());
        assertEquals(0, page.content().get(1).commentCount());
        assertTrue(page.content().get(1).reactions().isEmpty());
        verify(reactions, times(1)).findBySubmissionIds(List.of(10L, 11L));
        verify(comments, times(1)).countBySubmissionIds(List.of(10L, 11L));
    }

    private static LinkedMultiValueMap<String, String> form(String... pairs) {
        LinkedMultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.add(pairs[i], pairs[i + 1]);
        return map;
    }
    private static final LinkedMultiValueMap<String, MultipartFile> NO_FILES = new LinkedMultiValueMap<>();

    /** 제출이 통과하면 저장하려던 행을 돌려준다. */
    private SubmissionInsertRow saved(String template, MultiValueMap<String, String> fields) {
        mission.setTemplate(template);
        clearInvocations(submissions);
        doAnswer(invocation -> { invocation.getArgument(0, SubmissionInsertRow.class).setId(99L); return null; })
                .when(submissions).insert(any());
        SubmissionRow row = feedRow(99L);
        row.setTemplate(template);
        when(submissions.findDetailById(99L)).thenReturn(row);
        service.submit(3L, 1L, fields, NO_FILES);
        ArgumentCaptor<SubmissionInsertRow> inserted = ArgumentCaptor.forClass(SubmissionInsertRow.class);
        verify(submissions).insert(inserted.capture());
        return inserted.getValue();
    }
    private void rejects(String template, List<MultiValueMap<String, String>> bad) {
        mission.setTemplate(template);
        for (MultiValueMap<String, String> fields : bad)
            assertEquals("INVALID_SUBMISSION", error(fields, NO_FILES), template + " " + fields);
        byte[] jpg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
        assertEquals("INVALID_SUBMISSION", error(form(), files(jpg)), template + " file");
    }

    @Test void oneWordStoresWordAndOptionalReason() {
        SubmissionInsertRow row = saved("TEXT_ONE_WORD", form("text", " 설렘 ", "caption", "  "));
        assertEquals("{\"text\":\"설렘\"}", row.getAnswerData());
        assertNull(row.getCaption());
        assertNull(row.getNumberValue());
        assertNull(row.getChoiceValue());
        assertEquals("주말이라서", saved("TEXT_ONE_WORD", form("text", "😀".repeat(20), "caption", " 주말이라서 ")).getCaption());
        rejects("TEXT_ONE_WORD", List.of(form("text", "두 단어"), form("text", "줄\n바꿈"), form("text", "😀".repeat(21)),
                form("text", " "), form("caption", "이유만"), form("text", "단어", "caption", "가".repeat(101)),
                form("text", "단어", "number", "3"), form("text", "단어", "text", "단어")));
    }

    @Test void reviewStoresStarsAndLine() {
        SubmissionInsertRow row = saved("TEXT_REVIEW", form("number", "5", "text", "최고의 떡볶이"));
        assertEquals(5, row.getNumberValue());
        assertEquals("{\"text\":\"최고의 떡볶이\"}", row.getAnswerData());
        assertEquals(1, saved("TEXT_REVIEW", form("number", "1", "text", "😀".repeat(100))).getNumberValue());
        rejects("TEXT_REVIEW", List.of(form("number", "0", "text", "별로"), form("number", "6", "text", "최고"),
                form("number", "5.0", "text", "최고"), form("number", " 5", "text", "최고"), form("number", "", "text", "최고"),
                form("text", "별점 없음"), form("number", "4"), form("number", "4", "text", "😀".repeat(101)),
                form("number", "4", "text", "좋아요", "caption", "이유")));
    }

    @Test void balanceStoresChoiceAndOptionalReason() {
        SubmissionInsertRow row = saved("TEXT_BALANCE", form("choice", "B", "caption", " 시원해서 "));
        assertEquals("B", row.getChoiceValue());
        assertEquals("시원해서", row.getCaption());
        assertNull(row.getAnswerData());
        assertNull(saved("TEXT_BALANCE", form("choice", "A")).getCaption());
        rejects("TEXT_BALANCE", List.of(form("choice", "a"), form("choice", "C"), form("choice", "AB"), form(),
                form("choice", "A", "caption", "가".repeat(201)), form("choice", "A", "text", "이유")));
    }

    @Test void slidersStoreAWholeNumberFromZeroToHundred() {
        for (String template : List.of("SLIDER_POSITION", "SLIDER_TASTE")) {
            assertEquals(0, saved(template, form("number", "0")).getNumberValue());
            SubmissionInsertRow row = saved(template, form("number", "100"));
            assertEquals(100, row.getNumberValue());
            assertNull(row.getAnswerData());
            rejects(template, List.of(form("number", "-1"), form("number", "101"), form("number", "50.0"),
                    form("number", " 5"), form("number", ""), form("number", "abc"), form("number", "+5"),
                    form("number", "1000"), form(), form("number", "50", "guess", "50"), form("number", "5", "number", "6")));
        }
    }

    @Test void consensusStoresPositionAndGuess() {
        SubmissionInsertRow row = saved("SLIDER_CONSENSUS", form("number", "70", "guess", "55"));
        assertEquals(70, row.getNumberValue());
        assertEquals("{\"guess\":55}", row.getAnswerData());
        rejects("SLIDER_CONSENSUS", List.of(form("number", "70"), form("guess", "55"), form("number", "70", "guess", "101"),
                form("number", "70", "guess", "x"), form("number", "70", "guess", "55", "text", "왜")));
    }

    @Test void scalarAnswersComeBackInTheResponse() {
        when(submissions.existsByMissionIdAndUserId(1L, 3L)).thenReturn(true);
        SubmissionRow row = feedRow(50L);
        row.setTemplate("SLIDER_CONSENSUS");
        row.setNumberValue(70);
        row.setAnswerData("{\"guess\":55}");
        when(submissions.findDetailById(50L)).thenReturn(row);
        var response = service.detail(3L, 50L);
        assertEquals(70, response.number());
        assertEquals(55, response.guess());
        assertNull(response.choice());
        assertNull(response.text());
        row.setTemplate("TEXT_BALANCE");
        row.setNumberValue(null);
        row.setChoiceValue("A");
        row.setAnswerData(null);
        response = service.detail(3L, 50L);
        assertEquals("A", response.choice());
        assertNull(response.number());
        assertNull(response.guess());
    }

    private static SubmissionRow feedRow(Long id) {
        SubmissionRow row = new SubmissionRow();
        row.setId(id); row.setMissionId(1L); row.setGroupId(2L); row.setUserId(3L);
        row.setTemplate("TEXT_FILL_BLANK"); row.setSubmittedAt(LocalDateTime.now());
        return row;
    }
}
