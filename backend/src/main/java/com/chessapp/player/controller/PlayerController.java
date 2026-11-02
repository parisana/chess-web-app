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
    private final PlayerStatisticsService playerStatisticsService;

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

    @PatchMapping("/me")
    public PlayerResponse updateCurrentPlayer(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdatePlayerRequest request
    ) {
        // The validated JWT identifies the player allowed to change this profile.
        Long playerId = Long.parseLong(
                Objects.requireNonNull(jwt.getSubject())
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
            @AuthenticationPrincipal Jwt jwt
    ) {
        long playerId = Long.parseLong(
                Objects.requireNonNull(jwt.getSubject())
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
