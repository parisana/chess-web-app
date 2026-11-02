package com.chessapp.game.event;

import com.chessapp.game.entity.GameResult;

import java.util.Objects;

/**
 * @author pari on 11/1/26
 * Immutable completion details published inside the game transaction.
 * Contains no managed entities.
 */
public record GameFinishedEvent(
        long gameId,
        long whitePlayerId,
        long blackPlayerId,
        GameResult result
) {

    public GameFinishedEvent {
        if (gameId <= 0
                || whitePlayerId <= 0
                || blackPlayerId <= 0
                || whitePlayerId == blackPlayerId) {
            throw new IllegalArgumentException(
                    "Invalid finished-game identifiers"
            );
        }

        Objects.requireNonNull(result, "Game result is required");
    }
}
