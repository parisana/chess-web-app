package com.chessapp.game.controller;

import com.chessapp.game.dto.GameResponse;
import com.chessapp.game.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * @author pari on 10/23/26
 */
@RestController
@RequestMapping("/api/v1/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    @GetMapping("/{gameId}")
    public GameResponse getGame(
            @PathVariable("gameId") Long gameId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        // JwtConfig validates that the subject is a positive Long.
        // Use that authenticated identity rather than a client-supplied ID.
        Long requestingPlayerId = Long.parseLong(Objects.requireNonNull(jwt.getSubject()));

        return gameService.getGame(gameId, requestingPlayerId);
    }
}
