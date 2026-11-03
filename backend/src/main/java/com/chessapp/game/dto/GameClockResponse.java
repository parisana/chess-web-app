package com.chessapp.game.dto;

import com.chessapp.game.clock.GameClockCalculator;
import com.chessapp.game.entity.Game;
import com.chessapp.game.entity.GameStatus;
import com.chessapp.game.rules.model.PieceColor;

import java.time.Instant;
import java.util.Objects;
/**
 * @author pari on 11/3/26
 * Balances projected at calculatedAt, rather than the balances stored
 * when the current turn began.

 * runningColor is null for frozen, finished-game clocks.
 * An untimed game has a null GameClockResponse.
 */
public record GameClockResponse(
        long whiteRemainingMillis,
        long blackRemainingMillis,
        PieceColor runningColor,
        Instant calculatedAt // When the returned balances were calculated
) {

    public static GameClockResponse from(
            Game game,
            Instant calculatedAt
    ) {
        Objects.requireNonNull(game, "Game is required");
        Objects.requireNonNull(calculatedAt, "Capture time is required");

        if (!game.hasClock()) {
            return null;
        }

        if (game.getStatus() == GameStatus.FINISHED) {
            return new GameClockResponse(
                    game.getWhiteRemainingMillis(),
                    game.getBlackRemainingMillis(),
                    null,
                    calculatedAt
            );
        }

        var state = game.runningClockState();

        return new GameClockResponse(
                GameClockCalculator.remainingMillis(
                        state, PieceColor.WHITE, calculatedAt
                ),
                GameClockCalculator.remainingMillis(
                        state, PieceColor.BLACK, calculatedAt
                ),
                state.runningColor(),
                calculatedAt
        );
    }
}
