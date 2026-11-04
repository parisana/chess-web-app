package com.chessapp.matchmaking.controller;

import com.chessapp.matchmaking.dto.QueueStatusResponse;
import com.chessapp.matchmaking.service.MatchmakingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

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
    public QueueStatusResponse join(Principal principal) {
        return matchmakingService.join(playerId(principal));
    }

    @GetMapping("/me")
    public QueueStatusResponse status(Principal principal) {
        return matchmakingService.status(playerId(principal));
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(Principal principal) {
        matchmakingService.leave(playerId(principal));
    }

    private static Long playerId(Principal principal) {
        return Long.parseLong(principal.getName());
    }
}
