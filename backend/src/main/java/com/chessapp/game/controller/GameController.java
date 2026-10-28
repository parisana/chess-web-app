package com.chessapp.game.controller;

import com.chessapp.game.dto.GameMoveResult;
import com.chessapp.game.dto.GameResponse;
import com.chessapp.game.dto.SubmitMoveRequest;
import com.chessapp.game.service.GameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

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
        Long requestingPlayerId =
                Long.parseLong(Objects.requireNonNull(jwt.getSubject()));

        return gameService.getGame(gameId, requestingPlayerId);
    }

    @PostMapping("/{gameId}/moves")
    public GameMoveResult submitMove(
            @PathVariable("gameId") Long gameId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SubmitMoveRequest request
    ) {
        // The authenticated subject chooses the actor, never the request body.
        Long requestingPlayerId =
                Long.parseLong(Objects.requireNonNull(jwt.getSubject()));

        return gameService.submitMove(
                gameId,
                requestingPlayerId,
                request.expectedVersion(),
                request.toMove()
        );
    }
}
