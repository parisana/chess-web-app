package com.chessapp.player.controller;


import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/**
 * @author pari on 10/16/26
 */
@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;

    @GetMapping("/me")
    public PlayerResponse currentPlayer(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return playerService.findById(
                Long.parseLong(Objects.requireNonNull(jwt.getSubject()))
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Authenticated player no longer exists"
        ));
    }
}
