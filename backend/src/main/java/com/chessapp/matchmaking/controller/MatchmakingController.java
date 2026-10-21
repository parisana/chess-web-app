package com.chessapp.matchmaking.controller;

import com.chessapp.matchmaking.dto.QueueStatusResponse;
import com.chessapp.matchmaking.service.MatchmakingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

/**
 * @author pari on 10/21/26
 * | Method | Endpoint                          | Success |
 * |-------------------------------------------------------------------------------|
 * | POST   | `/api/v1/matchmaking/queue`       | `200` with queue status |
 * | GET    | `/api/v1/matchmaking/queue/me`    | `200` with queue status |
 * | DELETE | `/api/v1/matchmaking/queue/me`    | `204`, including repeated leave |
 */
@RestController
@RequestMapping("/api/v1/matchmaking/queue")
@RequiredArgsConstructor
public class MatchmakingController {

    private final MatchmakingService matchmakingService;

    @PostMapping
    public QueueStatusResponse join(@AuthenticationPrincipal Jwt jwt) {
        return matchmakingService.join(playerId(jwt));
    }

    @GetMapping("/me")
    public QueueStatusResponse status(@AuthenticationPrincipal Jwt jwt) {
        return matchmakingService.status(playerId(jwt));
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal Jwt jwt) {
        matchmakingService.leave(playerId(jwt));
    }

    private static Long playerId(Jwt jwt) {
        return Long.parseLong(
                Objects.requireNonNull(jwt.getSubject())
        );
    }
}
