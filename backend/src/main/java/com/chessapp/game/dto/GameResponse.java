package com.chessapp.game.dto;

import com.chessapp.game.entity.Game;
import com.chessapp.game.entity.GameResult;
import com.chessapp.game.entity.GameStatus;
import com.chessapp.game.entity.GameTerminationReason;

import java.time.Instant;

/**
 * @author pari on 10/22/26
 * For active games, the three completion fields remain null. version is present for both active and finished games.
 */
public record GameResponse(
        Long id,
        Long whitePlayerId,
        Long blackPlayerId,
        GameStatus status,
        String currentFen,
        Instant createdAt,
        Long version,
        GameResult result,
        GameTerminationReason terminationReason,
        Instant finishedAt,
        Long drawOfferedByPlayerId
) {
    public static GameResponse from(Game game) {
        return new GameResponse(
                game.getId(),
                game.getWhitePlayerId(),
                game.getBlackPlayerId(),
                game.getStatus(),
                game.getCurrentFen(),
                game.getCreatedAt(),
                game.getVersion(),
                game.getResult(),
                game.getTerminationReason(),
                game.getFinishedAt(),
                game.getDrawOfferedByPlayerId()
        );
    }
}
