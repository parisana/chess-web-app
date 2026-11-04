package com.chessapp.player.controller;


import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.dto.PlayerStatisticsResponse;
import com.chessapp.player.dto.UpdatePlayerRequest;
import com.chessapp.player.service.PlayerService;
import com.chessapp.player.service.PlayerStatisticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

/**
 * @author pari on 10/16/26
 */
@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;
    private final PlayerStatisticsService playerStatisticsService;

    @GetMapping("/me")
    public PlayerResponse currentPlayer(
            Principal principal
    ) {
        return playerService.findById(
                Long.parseLong(principal.getName())
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Authenticated player no longer exists"
        ));
    }

    @PatchMapping("/me")
    public PlayerResponse updateCurrentPlayer(
            Principal principal,
            @Valid @RequestBody UpdatePlayerRequest request
    ) {
        // The authenticated principal identifies the Player allowed to edit.
        Long playerId = Long.parseLong(
                principal.getName()
        );

        return playerService.updateUsername(
                playerId,
                request.username()
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Authenticated player no longer exists"
        ));
    }

    @GetMapping("/me/statistics")
    public ResponseEntity<PlayerStatisticsResponse> currentPlayerStatistics(
            Principal principal
    ) {
        long playerId = Long.parseLong(
                principal.getName()
        );

        var statistics = playerStatisticsService.findByPlayerId(playerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated player no longer exists"
                ));

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(statistics);
    }
}
