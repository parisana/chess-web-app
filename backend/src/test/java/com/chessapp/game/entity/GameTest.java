package com.chessapp.game.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class GameTest {

    private static final String BEFORE_MATE =
            "7k/8/5KQ1/8/8/8/8/8 w - - 4 20";

    private static final String AFTER_MATE =
            "7k/6Q1/5K2/8/8/8/8/8 b - - 5 20";

    @Test
    void rejectsInconsistentCompletionWithoutPartiallyChangingState() {
        var game = new Game(1L, 2L, BEFORE_MATE);
        var finishedAt = Instant.parse("2026-10-28T10:00:00Z");

        assertThrows(
                IllegalArgumentException.class,
                () -> game.finish(
                        GameResult.DRAW,
                        GameTerminationReason.CHECKMATE,
                        finishedAt
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> game.finish(
                        GameResult.WHITE_WIN,
                        GameTerminationReason.STALEMATE,
                        finishedAt
                )
        );

        assertAll(
                () -> assertEquals(
                        GameStatus.IN_PROGRESS,
                        game.getStatus()
                ),
                () -> assertEquals(
                        BEFORE_MATE,
                        game.getCurrentFen()
                ),
                () -> assertNull(game.getResult()),
                () -> assertNull(game.getTerminationReason()),
                () -> assertNull(game.getFinishedAt())
        );
    }

    @Test
    void completedGamePreservesItsFinalStateAndRejectsFurtherMutation() {
        var game = new Game(1L, 2L, BEFORE_MATE);
        var finishedAt = Instant.parse("2026-10-28T10:00:00Z");

        game.updatePosition(AFTER_MATE);
        game.finish(
                GameResult.WHITE_WIN,
                GameTerminationReason.CHECKMATE,
                finishedAt
        );

        assertThrows(
                IllegalStateException.class,
                () -> game.updatePosition(BEFORE_MATE)
        );

        assertThrows(
                IllegalStateException.class,
                () -> game.finish(
                        GameResult.DRAW,
                        GameTerminationReason.STALEMATE,
                        finishedAt.plusSeconds(1)
                )
        );

        assertAll(
                () -> assertEquals(
                        AFTER_MATE,
                        game.getCurrentFen()
                ),
                () -> assertEquals(
                        GameStatus.FINISHED,
                        game.getStatus()
                ),
                () -> assertEquals(
                        GameResult.WHITE_WIN,
                        game.getResult()
                ),
                () -> assertEquals(
                        GameTerminationReason.CHECKMATE,
                        game.getTerminationReason()
                ),
                () -> assertEquals(
                        finishedAt,
                        game.getFinishedAt()
                )
        );
    }

    @Test
    void newGameRetainsItsHistoryBaseline() {
        var game = new Game(1L, 2L, BEFORE_MATE);

        assertAll(
                () -> assertEquals(
                        BEFORE_MATE,
                        game.getHistoryStartFen()
                ),
                () -> assertTrue(game.isHistoryComplete()),
                () -> assertEquals(0L, game.getRecordedMoveCount())
        );
    }

    @Test
    void recordedMoveSequenceAdvancesWithoutChangingBaseline() {
        var game = new Game(1L, 2L, BEFORE_MATE);

        assertEquals(1L, game.advanceRecordedMoveCount());
        assertEquals(2L, game.advanceRecordedMoveCount());

        assertEquals(BEFORE_MATE, game.getHistoryStartFen());
    }

    @Test
    void finishedGameCannotAdvanceItsRecordedMoveSequence() {
        var game = new Game(1L, 2L, BEFORE_MATE);

        game.finish(
                GameResult.WHITE_WIN,
                GameTerminationReason.CHECKMATE,
                Instant.parse("2026-10-30T06:00:00Z")
        );

        assertThrows(
                IllegalStateException.class,
                game::advanceRecordedMoveCount
        );

        assertEquals(0L, game.getRecordedMoveCount());
    }
}