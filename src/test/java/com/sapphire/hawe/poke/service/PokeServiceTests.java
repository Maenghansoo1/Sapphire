package com.sapphire.hawe.poke.service;

import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.ErrorCode;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.group.service.GroupErrorCode;
import com.sapphire.hawe.poke.dao.PokeMapper;
import com.sapphire.hawe.poke.domain.Poke;
import com.sapphire.hawe.poke.dto.PokeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PokeServiceTests {

    @Mock PokeMapper pokeMapper;
    @Mock GroupMapper groupMapper;
    @Mock GroupMemberMapper groupMemberMapper;

    private PokeService service;

    @BeforeEach
    void setUp() {
        service = new PokeService(pokeMapper, groupMapper, groupMemberMapper);
    }

    @Test
    void sendPokeSavesPokeAndReturnsIt() {
        when(groupMapper.findById(11L)).thenReturn(group(11L));
        when(groupMemberMapper.existsByGroupIdAndUserId(11L, 7L)).thenReturn(true);
        when(groupMemberMapper.existsByGroupIdAndUserId(11L, 8L)).thenReturn(true);
        doAnswer(invocation -> {
            Poke poke = invocation.getArgument(0);
            poke.setId(1L);
            return null;
        }).when(pokeMapper).insert(any(Poke.class));

        var response = service.sendPoke(7L, 11L, new PokeRequest(8L, "fling"));

        assertEquals(1L, response.id());
        assertEquals(8L, response.toUserId());
        assertEquals("fling", response.type());
        assertNotNull(response.sentAt());
        ArgumentCaptor<Poke> saved = ArgumentCaptor.forClass(Poke.class);
        verify(pokeMapper).insert(saved.capture());
        assertEquals(7L, saved.getValue().getFromUserId());
        assertEquals(11L, saved.getValue().getGroupId());
    }

    @Test
    void missingGroupReturnsNotFound() {
        assertFailsWith(GroupErrorCode.GROUP_NOT_FOUND, new PokeRequest(8L, "poke"));
    }

    @Test
    void senderOutsideGroupCannotPoke() {
        when(groupMapper.findById(11L)).thenReturn(group(11L));

        assertFailsWith(GroupErrorCode.NOT_GROUP_MEMBER, new PokeRequest(8L, "poke"));
    }

    @Test
    void cannotPokeSelf() {
        when(groupMapper.findById(11L)).thenReturn(group(11L));
        when(groupMemberMapper.existsByGroupIdAndUserId(11L, 7L)).thenReturn(true);

        assertFailsWith(PokeErrorCode.CANNOT_POKE_SELF, new PokeRequest(7L, "poke"));
    }

    @Test
    void targetOutsideGroupReturnsNotFound() {
        when(groupMapper.findById(11L)).thenReturn(group(11L));
        when(groupMemberMapper.existsByGroupIdAndUserId(11L, 7L)).thenReturn(true);

        assertFailsWith(PokeErrorCode.TARGET_NOT_GROUP_MEMBER, new PokeRequest(8L, "poke"));
    }

    @Test
    void targetWhoSubmittedTodayCannotBePoked() {
        when(groupMapper.findById(11L)).thenReturn(group(11L));
        when(groupMemberMapper.existsByGroupIdAndUserId(11L, 7L)).thenReturn(true);
        when(groupMemberMapper.existsByGroupIdAndUserId(11L, 8L)).thenReturn(true);
        when(pokeMapper.existsCurrentMissionSubmission(11L, 8L)).thenReturn(true);

        assertFailsWith(PokeErrorCode.TARGET_ALREADY_SUBMITTED, new PokeRequest(8L, "poke"));
    }

    private void assertFailsWith(ErrorCode expected, PokeRequest request) {
        BusinessException error = assertThrows(BusinessException.class, () -> service.sendPoke(7L, 11L, request));
        assertEquals(expected, error.errorCode());
        verify(pokeMapper, never()).insert(any(Poke.class));
    }

    private static Group group(Long id) {
        Group group = new Group();
        group.setId(id);
        group.setOwnerId(7L);
        return group;
    }
}
