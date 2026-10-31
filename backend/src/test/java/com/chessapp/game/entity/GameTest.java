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

    @Test
    void drawOfferPreservesPositionAndRecordedMoveCount() {
        var game = new Game(1L, 2L, BEFORE_MATE);

        // Black may offer even though the position has White to move.
        game.offerDraw(2L);

        assertAll(
                () -> assertEquals(
                        Long.valueOf(2),
                        game.getDrawOfferedByPlayerId()
                ),
                () -> assertEquals(BEFORE_MATE, game.getCurrentFen()),
                () -> assertEquals(0L, game.getRecordedMoveCount()),
                () -> assertEquals(
                        GameStatus.IN_PROGRESS,
                        game.getStatus()
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> game.offerDraw(1L)
        );

        assertEquals(
                Long.valueOf(2),
                game.getDrawOfferedByPlayerId()
        );
    }

    @Test
    void outsiderCannotCreateDrawOffer() {
        var game = new Game(1L, 2L, BEFORE_MATE);

        assertThrows(
                IllegalArgumentException.class,
                () -> game.offerDraw(3L)
        );

        assertNull(game.getDrawOfferedByPlayerId());
    }

    @Test
    void clearingDrawOfferAllowsAnotherOffer() {
        var game = new Game(1L, 2L, BEFORE_MATE);

        game.offerDraw(1L);
        game.clearDrawOffer();

        assertNull(game.getDrawOfferedByPlayerId());

        game.offerDraw(2L);

        assertEquals(
                Long.valueOf(2),
                game.getDrawOfferedByPlayerId()
        );
    }

    @Test
    void acceptedPositionUpdateClearsPendingDrawOffer() {
        var game = new Game(1L, 2L, BEFORE_MATE);

        game.offerDraw(2L);
        game.updatePosition(AFTER_MATE);

        assertNull(game.getDrawOfferedByPlayerId());
        assertEquals(AFTER_MATE, game.getCurrentFen());
    }

    @Test
    void invalidPositionUpdatePreservesPendingDrawOffer() {
        var game = new Game(1L, 2L, BEFORE_MATE);

        game.offerDraw(1L);

        assertThrows(
                IllegalArgumentException.class,
                () -> game.updatePosition(" ")
        );

        assertEquals(BEFORE_MATE, game.getCurrentFen());
        assertEquals(
                Long.valueOf(1),
                game.getDrawOfferedByPlayerId()
        );
    }

    @Test
    void drawAgreementRequiresDrawAndClearsPendingOffer() {
        var game = new Game(1L, 2L, BEFORE_MATE);
        var finishedAt = Instant.parse("2026-10-30T10:00:00Z");

        game.offerDraw(1L);

        assertThrows(
                IllegalArgumentException.class,
                () -> game.finish(
                        GameResult.WHITE_WIN,
                        GameTerminationReason.DRAW_AGREEMENT,
                        finishedAt
                )
        );

        // Failed validation must preserve the active game and pending offer.
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        assertEquals(
                Long.valueOf(1),
                game.getDrawOfferedByPlayerId()
        );

        game.finish(
                GameResult.DRAW,
                GameTerminationReason.DRAW_AGREEMENT,
                finishedAt
        );

        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertEquals(GameResult.DRAW, game.getResult());
        assertEquals(
                GameTerminationReason.DRAW_AGREEMENT,
                game.getTerminationReason()
        );
        assertNull(game.getDrawOfferedByPlayerId());
        assertEquals(BEFORE_MATE, game.getCurrentFen());
        assertEquals(0L, game.getRecordedMoveCount());

        assertThrows(
                IllegalStateException.class,
                () -> game.offerDraw(2L)
        );
    }
}