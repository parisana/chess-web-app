package com.chessapp.game.clock;

import com.chessapp.game.rules.model.PieceColor;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;


/**
 * @author pari on 11/2/26

 * Pure clock arithmetic using server-supplied timestamps.
 * No Spring, persistence, scheduling or system-clock access belongs here.
 * Balances use whole milliseconds.
 */
public final class GameClockCalculator {

    private GameClockCalculator() {
    }

    public static GameClockState start(
            long initialTimeMillis,
            Instant startedAt
    ) {
        if (initialTimeMillis <= 0) {
            throw new IllegalArgumentException(
                    "Initial time must be positive"
            );
        }

        return new GameClockState(
                initialTimeMillis,
                initialTimeMillis,
                PieceColor.WHITE,
                startedAt
        );
    }

    /**
     * Returns the requested Player's balance at the supplied timestamp.

     * Only the running clock loses time. Expired balances are clamped to
     * zero. A timestamp before the turn started spends no time and never
     * increases the stored balance.
     */
    public static long remainingMillis(
            GameClockState state,
            PieceColor color,
            Instant now
    ) {
        Objects.requireNonNull(state, "Clock state is required");
        Objects.requireNonNull(color, "Color is required");
        Objects.requireNonNull(now, "Current time is required");

        long storedRemainingMillis = state.storedRemainingMillis(color);

        if (color != state.runningColor()
                || !now.isAfter(state.turnStartedAt())) {
            return storedRemainingMillis;
        }

        Duration elapsed = Duration.between(
                state.turnStartedAt(),
                now
        );

        // Compare before converting elapsed time to milliseconds.
        // Very distant timestamps must expire, not overflow toMillis().
        if (elapsed.compareTo(Duration.ofMillis(storedRemainingMillis)) >= 0) {
            return 0;
        }

        return storedRemainingMillis - elapsed.toMillis();
    }

    /**
     * The running Player has expired at the exact deadline or afterward.
     */
    public static boolean isExpired(
            GameClockState state,
            Instant now
    ) {
        Objects.requireNonNull(state, "Clock state is required");

        return remainingMillis(
                state,
                state.runningColor(),
                now
        ) == 0;
    }

    /**
     * Returns the running Player's deadline from the captured state.
     * Reading this value does not mutate or persist anything.
     */
    public static Instant deadline(GameClockState state) {
        Objects.requireNonNull(state, "Clock state is required");

        return state.turnStartedAt().plusMillis(
                state.storedRemainingMillis(state.runningColor())
        );
    }

    /**
     * Deducts the mover's elapsed time and starts the opponent's clock.

     * The caller must separately validate the move and persist this state
     * atomically with the accepted game change. An expired clock cannot
     * advance to another turn.
     */
    public static GameClockState afterAcceptedMove(
            GameClockState state,
            Instant acceptedAt
    ) {
        Objects.requireNonNull(state, "Clock state is required");
        Objects.requireNonNull(
                acceptedAt,
                "Move acceptance time is required"
        );

        long moverRemaining = remainingMillis(
                state,
                state.runningColor(),
                acceptedAt
        );

        if (moverRemaining == 0) {
            throw new IllegalStateException(
                    "An expired clock cannot accept a move"
            );
        }

        // A backward wall-clock adjustment must not move the next turn's
        // boundary earlier and manufacture additional elapsed time.
        Instant nextTurnStartedAt =
                acceptedAt.isBefore(state.turnStartedAt())
                        ? state.turnStartedAt()
                        : acceptedAt;

        if (state.runningColor() == PieceColor.WHITE) {
            return new GameClockState(
                    moverRemaining,
                    state.blackRemainingMillis(),
                    PieceColor.BLACK,
                    nextTurnStartedAt
            );
        }

        return new GameClockState(
                state.whiteRemainingMillis(),
                moverRemaining,
                PieceColor.WHITE,
                nextTurnStartedAt
        );
    }
}
