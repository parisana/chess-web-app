package com.chessapp.game.controller;

import com.chessapp.game.dto.*;
import com.chessapp.game.service.GameExportService;
import com.chessapp.game.service.GameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.Principal;

/**
 * @author pari on 10/23/26
 */
@RestController
@RequestMapping("/api/v1/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;
    private final GameExportService gameExportService;

    @GetMapping("/{gameId}")
    public GameResponse getGame(
            @PathVariable("gameId") Long gameId,
            Principal principal
    ) {
        Long requestingPlayerId = Long.parseLong(principal.getName());

        return gameService.getGame(gameId, requestingPlayerId);
    }

    @GetMapping("/{gameId}/moves")
    public GameHistoryResponse getHistory(
            @PathVariable("gameId") Long gameId,
            Principal principal
    ) {
        Long requestingPlayerId = Long.parseLong(principal.getName());

        return gameService.getHistory(gameId, requestingPlayerId);
    }

    @PostMapping("/{gameId}/moves")
    public GameMoveResult submitMove(
            @PathVariable("gameId") Long gameId,
            Principal principal,
            @Valid @RequestBody SubmitMoveRequest request
    ) {
        Long requestingPlayerId = Long.parseLong(principal.getName());

        return gameService.submitMove(
                gameId,
                requestingPlayerId,
                request.expectedVersion(),
                request.toMove()
        );
    }

    @PostMapping("/{gameId}/resignation")
    public GameResponse resign(
            @PathVariable("gameId") Long gameId,
            Principal principal,
            @Valid @RequestBody ResignGameRequest request
    ) {
        // The authenticated subject determines who resigns.
        Long requestingPlayerId = Long.parseLong(principal.getName());

        return gameService.resign(
                gameId,
                requestingPlayerId,
                request.expectedVersion()
        );
    }

    @PostMapping("/{gameId}/draw-offer")
    public GameResponse offerDraw(
            @PathVariable("gameId") Long gameId,
            Principal principal,
            @Valid @RequestBody DrawOfferRequest request
    ) {
        Long requestingPlayerId = Long.parseLong(principal.getName());

        return gameService.offerDraw(
                gameId,
                requestingPlayerId,
                request.expectedVersion()
        );
    }

    @PostMapping("/{gameId}/draw-offer/accept")
    public GameResponse acceptDraw(
            @PathVariable("gameId") Long gameId,
            Principal principal,
            @Valid @RequestBody DrawOfferRequest request
    ) {
        Long requestingPlayerId = Long.parseLong(principal.getName());

        return gameService.acceptDraw(
                gameId,
                requestingPlayerId,
                request.expectedVersion()
        );
    }

    @PostMapping("/{gameId}/draw-offer/decline")
    public GameResponse declineDraw(
            @PathVariable("gameId") Long gameId,
            Principal principal,
            @Valid @RequestBody DrawOfferRequest request
    ) {
        Long requestingPlayerId = Long.parseLong(principal.getName());

        return gameService.declineDraw(
                gameId,
                requestingPlayerId,
                request.expectedVersion()
        );
    }

    @PostMapping("/{gameId}/draw-offer/withdraw")
    public GameResponse withdrawDraw(
            @PathVariable("gameId") Long gameId,
            Principal principal,
            @Valid @RequestBody DrawOfferRequest request
    ) {
        Long requestingPlayerId = Long.parseLong(principal.getName());

        return gameService.withdrawDraw(
                gameId,
                requestingPlayerId,
                request.expectedVersion()
        );
    }

    @GetMapping("/{gameId}/pgn")
    public ResponseEntity<byte[]> exportPgn(
            @PathVariable("gameId") Long gameId,
            Principal principal
    ) {
        Long requestingPlayerId = Long.parseLong(principal.getName());

        String pgn = gameExportService.exportPgn(
                gameId,
                requestingPlayerId
        );

        String disposition = ContentDisposition.attachment()
                .filename("game-" + gameId + ".pgn")
                .build()
                .toString();

        return ResponseEntity.ok()
                .contentType(new MediaType(
                        "application",
                        "x-chess-pgn",
                        StandardCharsets.UTF_8
                ))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pgn.getBytes(StandardCharsets.UTF_8));
    }
}
