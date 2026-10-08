package com.sapphire.hawe.poke.controller;

import com.sapphire.hawe.poke.dto.PokeRequest;
import com.sapphire.hawe.poke.dto.PokeResponse;
import com.sapphire.hawe.poke.service.PokeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}/pokes")
public class PokeController {

    private final PokeService pokeService;

    public PokeController(PokeService pokeService) {
        this.pokeService = pokeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PokeResponse sendPoke(@AuthenticationPrincipal Long userId, @PathVariable Long groupId,
            @Valid @RequestBody PokeRequest request) {
        return pokeService.sendPoke(userId, groupId, request);
    }
}
