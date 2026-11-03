package com.chessapp.game.entity;

import com.chessapp.game.clock.GameClockState;
import com.chessapp.game.rules.model.PieceColor;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 11/2/26
 */
class GameClockEntityTest {

    private static final Instant START =
            Instant.parse("2026-11-02T08:00:00Z");

    private static final String INITIAL_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/"
                    + "RNBQKBNR w KQkq - 0 1";

    @Test
    void untimedGamesRetainTheirExistingLifecycle() {
        var game = newGame();

        assertFalse(game.hasClock());

        game.advanceClockAfterAcceptedMove(START);

        game.finish(
                GameResult.WHITE_WIN,
                GameTerminationReason.RESIGNATION,
                START.plusSeconds(5)
        );

        assertAll(
                () -> assertEquals(
                        GameStatus.FINISHED,
                        game.getStatus()
                ),
                () -> assertNull(game.getWhiteRemainingMillis()),
                () -> assertNull(game.getBlackRemainingMillis()),
                () -> assertNull(game.getClockRunningColor()),
                () -> assertNull(game.getClockTurnStartedAt())
        );
    }

    @Test
    void initializationCannotResetAnExistingClock() {
        var game = newGame();

        game.startClock(600_000, START);

        assertEquals(
                new GameClockState(
                        600_000,
                        600_000,
                        PieceColor.WHITE,
                        START
                ),
                game.runningClockState()
        );

        assertThrows(
                IllegalStateException.class,
                () -> game.startClock(
                        900_000,
                        START.plusSeconds(10)
                )
        );

        assertEquals(600_000L, game.getWhiteRemainingMillis().longValue());
        assertEquals(START, game.getClockTurnStartedAt());
    }

    @Test
    void rejectedInitializationLeavesGameUntimed() {
        var game = newGame();

        assertThrows(
                IllegalArgumentException.class,
                () -> game.startClock(0, START)
        );

        assertFalse(game.hasClock());
        assertNull(game.getBlackRemainingMillis());
        assertNull(game.getClockRunningColor());
        assertNull(game.getClockTurnStartedAt());

        game.advanceRecordedMoveCount();

        assertThrows(
                IllegalStateException.class,
                () -> game.startClock(600_000, START)
        );

        assertFalse(game.hasClock());
    }

    @Test
    void completionFreezesTheRunningPlayersBalance() {
        var game = newGame();

        game.startClock(600_000, START);

        game.advanceClockAfterAcceptedMove(
                START.plusSeconds(3)
        );

        // Black's clock has run for five seconds when White resigns.
        game.finish(
                GameResult.BLACK_WIN,
                GameTerminationReason.RESIGNATION,
                START.plusSeconds(8)
        );

        assertAll(
                () -> assertTrue(game.hasClock()),
                () -> assertEquals(
                        597_000L,
                        game.getWhiteRemainingMillis().longValue()
                ),
                () -> assertEquals(
                        595_000L,
                        game.getBlackRemainingMillis().longValue()
                ),
                () -> assertNull(game.getClockRunningColor()),
                () -> assertNull(game.getClockTurnStartedAt()),
                () -> assertEquals(
                        GameStatus.FINISHED,
                        game.getStatus()
                )
        );

        assertThrows(
                IllegalStateException.class,
                game::runningClockState
        );

        assertThrows(
                IllegalStateException.class,
                () -> game.advanceClockAfterAcceptedMove(
                        START.plusSeconds(9)
                )
        );

        assertEquals(
                595_000L,
                game.getBlackRemainingMillis().longValue()
        );
    }

    @Test
    void terminalMoveAndCompletionUseTheSameTimestamp() {
        var game = newGame();

        game.startClock(600_000, START);

        Instant playedAt = START.plusSeconds(4);

        game.advanceClockAfterAcceptedMove(playedAt);
        game.finish(
                GameResult.WHITE_WIN,
                GameTerminationReason.CHECKMATE,
                playedAt
        );

        assertEquals(
                596_000L,
                game.getWhiteRemainingMillis().longValue()
        );
        assertEquals(
                600_000L,
                game.getBlackRemainingMillis().longValue()
        );
        assertNull(game.getClockTurnStartedAt());
    }

    @Test
    void invalidCompletionPreservesClockAndPendingOffer() {
        var game = newGame();

        game.startClock(600_000, START);
        game.offerDraw(1L);

        var before = game.runningClockState();

        assertThrows(
                IllegalArgumentException.class,
                () -> game.finish(
                        GameResult.WHITE_WIN,
                        GameTerminationReason.DRAW_AGREEMENT,
                        START.plusSeconds(5)
                )
        );

        assertEquals(before, game.runningClockState());
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        assertEquals(
                Long.valueOf(1),
                game.getDrawOfferedByPlayerId()
        );
        assertNull(game.getFinishedAt());
    }

    @Test
    void expiredMoveCannotPartiallyChangeClockState() {
        var game = newGame();

        game.startClock(1_000, START);

        var before = game.runningClockState();

        assertThrows(
                IllegalStateException.class,
                () -> game.advanceClockAfterAcceptedMove(
                        START.plusSeconds(1)
                )
        );

        assertEquals(before, game.runningClockState());
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
    }

    private static Game newGame() {
        return new Game(1L, 2L, INITIAL_FEN);
    }
}