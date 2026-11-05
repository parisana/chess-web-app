package com.chessapp.game.controller;

import com.chessapp.game.dto.*;
import com.chessapp.game.service.GameExportService;
import com.chessapp.game.service.GameQueryService;
import com.chessapp.game.service.GameCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.Principal;

/**
 * @author pari on 10/23/26
 */
@RestController
@RequestMapping("/api/v1/games")
@RequiredArgsConstructor
public class GameController {

    private final GameCommandService gameCommandService;
    private final GameQueryService gameQueryService;
    private final GameExportService gameExportService;

    @GetMapping("/{gameId}")
    public GameResponse getGame(
            @PathVariable("gameId") Long gameId,
            Principal principal
    ) {
        Long requestingPlayerId = Long.parseLong(principal.getName());

        return gameQueryService.getGame(gameId, requestingPlayerId);
    }

    @GetMapping("/{gameId}/moves")
    public GameHistoryResponse getHistory(
            @PathVariable("gameId") Long gameId,
            Principal principal
    ) {
        Long requestingPlayerId = Long.parseLong(principal.getName());

        return gameQueryService.getHistory(gameId, requestingPlayerId);
    }

    @GetMapping
    public ResponseEntity<PlayedGamesResponse> getPlayedGames(Principal principal, @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "20") int size) {
        // Reject invalid client input before calling the application service.
        if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Use page >= 0 and size between 1 and 50 "
                            + "within the supported pagination range"
            );
        }
        long requestingPlayerId = Long.parseLong(principal.getName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(gameQueryService.getPlayedGames(requestingPlayerId, page, size));
    }

    @PostMapping("/{gameId}/moves")
    public GameMoveResult submitMove(
            @PathVariable("gameId") Long gameId,
            Principal principal,
            @Valid @RequestBody SubmitMoveRequest request
    ) {
        Long requestingPlayerId = Long.parseLong(principal.getName());

        return gameCommandService.submitMove(
                gameId,
                requestingPlayerId,
                request.expectedVersion(),
                request.toMove()
        );
    }

    @GetMapping("/{gameId}/legal-moves")
    public ResponseEntity<GameLegalMovesResponse> getLegalMoves(
            @PathVariable("gameId") Long gameId,
            Principal principal
    ) {
        Long requestingPlayerId = Long.parseLong(principal.getName());

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(gameQueryService.getLegalMoves(
                        gameId,
                        requestingPlayerId
                ));
    }

    @PostMapping("/{gameId}/resignation")
    public GameResponse resign(
            @PathVariable("gameId") Long gameId,
            Principal principal,
            @Valid @RequestBody ResignGameRequest request
    ) {
        // The authenticated subject determines who resigns.
        Long requestingPlayerId = Long.parseLong(principal.getName());

        return gameCommandService.resign(
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

        return gameCommandService.offerDraw(
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

        return gameCommandService.acceptDraw(
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

        return gameCommandService.declineDraw(
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

        return gameCommandService.withdrawDraw(
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
