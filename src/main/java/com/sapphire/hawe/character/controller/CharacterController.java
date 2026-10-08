package com.sapphire.hawe.character.controller;

import com.sapphire.hawe.character.dto.CharacterResponse;
import com.sapphire.hawe.character.dto.CharacterUpdateRequest;
import com.sapphire.hawe.character.dto.MyCharacterResponse;
import com.sapphire.hawe.character.service.CharacterService;
import com.sapphire.hawe.global.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CharacterController {

    private final CharacterService characterService;

    public CharacterController(CharacterService characterService) {
        this.characterService = characterService;
    }

    @GetMapping("/characters")
    public PageResponse<CharacterResponse> listCharacters(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return characterService.listCharacters(page, size);
    }

    @PatchMapping("/me/character")
    public MyCharacterResponse changeMyCharacter(@AuthenticationPrincipal Long userId,
            @Valid @RequestBody CharacterUpdateRequest request) {
        return characterService.changeMyCharacter(userId, request);
    }
}
