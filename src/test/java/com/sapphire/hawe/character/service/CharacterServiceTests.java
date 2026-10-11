package com.sapphire.hawe.character.service;

import com.sapphire.hawe.auth.dao.UserMapper;
import com.sapphire.hawe.auth.domain.User;
import com.sapphire.hawe.character.dao.CharacterMapper;
import com.sapphire.hawe.character.domain.ProfileCharacter;
import com.sapphire.hawe.character.dto.CharacterUpdateRequest;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.security.TokenErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CharacterServiceTests {

    @Mock CharacterMapper characterMapper;
    @Mock UserMapper userMapper;

    private CharacterService service;

    @BeforeEach
    void setUp() {
        service = new CharacterService(characterMapper, userMapper);
    }

    @Test
    void listCharactersMapsRowsAndPageInfo() {
        when(characterMapper.findAll(0L, 20)).thenReturn(List.of(character(1L, "몽실이", "mongsil"),
                character(3L, "곰돌이", "gomdol")));
        when(characterMapper.count()).thenReturn(8L);

        var response = service.listCharacters(0, 20);

        assertEquals(2, response.content().size());
        assertEquals("gomdol", response.content().get(1).svgKey());
        assertEquals(8L, response.totalElements());
        assertEquals(1, response.totalPages());
    }

    @Test
    void invalidPageAndSizeFallBackToDefaults() {
        when(characterMapper.findAll(0L, 20)).thenReturn(List.of());

        var response = service.listCharacters(-1, 0);

        assertEquals(0, response.page());
        assertEquals(20, response.size());
    }

    @Test
    void missingUserDoesNotReturnSuccess() {
        when(characterMapper.findById(3L)).thenReturn(character(3L, "곰돌이", "gomdol"));
        when(characterMapper.updateUserCharacterId(42L, 3L)).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changeMyCharacter(42L, new CharacterUpdateRequest(3L)));

        assertEquals(TokenErrorCode.INVALID_TOKEN, error.errorCode());
        verify(userMapper).findById(42L);
    }

    @Test
    void selectingCurrentCharacterStillSucceedsWhenUpdateCountIsZero() {
        when(characterMapper.findById(3L)).thenReturn(character(3L, "곰돌이", "gomdol"));
        when(characterMapper.updateUserCharacterId(42L, 3L)).thenReturn(0);
        when(userMapper.findById(42L)).thenReturn(new User());

        var response = service.changeMyCharacter(42L, new CharacterUpdateRequest(3L));

        assertEquals(3L, response.characterId());
        assertEquals("gomdol", response.svgKey());
    }

    @Test
    void changeMyCharacterUpdatesUserAndReturnsCharacter() {
        when(characterMapper.findById(3L)).thenReturn(character(3L, "곰돌이", "gomdol"));
        when(characterMapper.updateUserCharacterId(7L, 3L)).thenReturn(1);

        var response = service.changeMyCharacter(7L, new CharacterUpdateRequest(3L));

        assertEquals(3L, response.characterId());
        assertEquals("곰돌이", response.name());
        assertEquals("gomdol", response.svgKey());
        verify(characterMapper).updateUserCharacterId(7L, 3L);
        verifyNoInteractions(userMapper);
    }

    @Test
    void unknownCharacterReturnsNotFoundWithoutUpdating() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changeMyCharacter(7L, new CharacterUpdateRequest(999L)));

        assertEquals(CharacterErrorCode.CHARACTER_NOT_FOUND, error.errorCode());
        verify(characterMapper, never()).updateUserCharacterId(anyLong(), any());
    }

    private static ProfileCharacter character(Long id, String name, String svgKey) {
        ProfileCharacter character = new ProfileCharacter();
        character.setId(id);
        character.setName(name);
        character.setSvgKey(svgKey);
        return character;
    }
}
