package com.chessapp.game.clock;

import com.chessapp.game.rules.model.PieceColor;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable clock values captured when the current turn started.

 * Remaining times are stored at that boundary rather than continuously
 * decremented. The running Player's current balance is calculated from
 * turnStartedAt and a server-supplied timestamp.

 * This represents a running clock. Finished-game persistence will store
 * frozen balances separately from the active-turn timestamp.
 */
public record GameClockState(
        long whiteRemainingMillis,
        long blackRemainingMillis,
        PieceColor runningColor,
        Instant turnStartedAt
) {

    public GameClockState {
        if (whiteRemainingMillis < 0 || blackRemainingMillis < 0) {
            throw new IllegalArgumentException(
                    "Remaining time must not be negative"
            );
        }

        Objects.requireNonNull(
                runningColor,
                "Running color is required"
        );
        Objects.requireNonNull(
                turnStartedAt,
                "Turn start time is required"
        );
    }

    public long storedRemainingMillis(PieceColor color) {
        Objects.requireNonNull(color, "Color is required");

        return color == PieceColor.WHITE
                ? whiteRemainingMillis
                : blackRemainingMillis;
    }
}