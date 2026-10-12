package com.sapphire.hawe.submission.service;

import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.storage.FileStorage;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.mission.dao.MissionMapper;
import com.sapphire.hawe.mission.dao.MissionRow;
import com.sapphire.hawe.submission.dao.SubmissionMapper;
import com.sapphire.hawe.submission.dao.SubmissionRow;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    SubmissionService service = new SubmissionService(missions, members, submissions, storage, new ObjectMapper());
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

    @Test void imageAndVoiceRules() {
        mission.setTemplate("PHOTO_NOW");
        assertEquals("INVALID_SUBMISSION", error(new LinkedMultiValueMap<>(), new LinkedMultiValueMap<>()));
        assertEquals("UNSUPPORTED_FILE_TYPE", error(new LinkedMultiValueMap<>(), files("not an image".getBytes())));
        assertEquals("FILE_TOO_LARGE", error(new LinkedMultiValueMap<>(), files(new byte[10 * 1024 * 1024 + 1])));
        mission.setTemplate("VOICE_LINE");
        byte[] audio = "ID3payload".getBytes();
        assertEquals("UNSUPPORTED_FILE_TYPE", error(new LinkedMultiValueMap<>(), files(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})));
        assertEquals("INVALID_SUBMISSION", error(fields("duration", "30.01"), files(audio)));
        assertEquals("INVALID_SUBMISSION", error(fields("duration", "NaN"), files(audio)));
        LinkedMultiValueMap<String, String> valid = fields("duration", "30"); valid.add("waveform", "[0,1]");
        doThrow(new DuplicateKeyException("duplicate")).when(submissions).insert(any());
        when(storage.store(eq("submissions"), any(), eq("mp3"))).thenReturn("submissions/test.mp3");
        assertEquals("ALREADY_SUBMITTED", error(valid, files(audio)));
        verify(storage).delete("submissions/test.mp3");
        valid.set("waveform", "[1.01]");
        assertEquals("INVALID_SUBMISSION", error(valid, files(audio)));
        valid.set("waveform", "[0," + "1,".repeat(63) + "0]");
        assertEquals("INVALID_SUBMISSION", error(valid, files(audio)));
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
}
