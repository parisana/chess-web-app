package com.chessapp.game.clock;

import com.chessapp.game.rules.model.PieceColor;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;


/**
 * @author pari on 11/2/26
 */
class GameClockCalculatorTest {

    private static final Instant START =
            Instant.parse("2026-11-02T00:00:00Z");

    private static final long TEN_MINUTES = 600_000;

    @Test
    void newGameStartsWhiteWithEqualBalances() {
        var state = GameClockCalculator.start(
                TEN_MINUTES,
                START
        );

        assertEquals(TEN_MINUTES, state.whiteRemainingMillis());
        assertEquals(TEN_MINUTES, state.blackRemainingMillis());
        assertEquals(PieceColor.WHITE, state.runningColor());
        assertEquals(START, state.turnStartedAt());

        assertEquals(
                START.plusSeconds(600),
                GameClockCalculator.deadline(state)
        );
    }

    @Test
    void onlyRunningPlayerSpendsTimeAndReadsDoNotMutateState() {
        var state = GameClockCalculator.start(
                TEN_MINUTES,
                START
        );

        assertEquals(
                TEN_MINUTES - 5_000,
                GameClockCalculator.remainingMillis(
                        state,
                        PieceColor.WHITE,
                        START.plusSeconds(5)
                )
        );
        assertEquals(
                TEN_MINUTES,
                GameClockCalculator.remainingMillis(
                        state,
                        PieceColor.BLACK,
                        START.plusSeconds(5)
                )
        );

        // Repeated reads calculate from the original turn boundary.
        assertEquals(
                TEN_MINUTES - 7_000,
                GameClockCalculator.remainingMillis(
                        state,
                        PieceColor.WHITE,
                        START.plusSeconds(7)
                )
        );
        assertEquals(TEN_MINUTES, state.whiteRemainingMillis());
    }

    @Test
    void acceptedMovesDeductEachPlayersOwnTimeAndSwitchTurns() {
        var initial = GameClockCalculator.start(
                TEN_MINUTES,
                START
        );

        var afterWhite = GameClockCalculator.afterAcceptedMove(
                initial,
                START.plusSeconds(3)
        );

        assertEquals(TEN_MINUTES - 3_000, afterWhite.whiteRemainingMillis());
        assertEquals(TEN_MINUTES, afterWhite.blackRemainingMillis());
        assertEquals(PieceColor.BLACK, afterWhite.runningColor());
        assertEquals(
                START.plusSeconds(3),
                afterWhite.turnStartedAt()
        );

        // Black spends five seconds, measured from White's move.
        var afterBlack = GameClockCalculator.afterAcceptedMove(
                afterWhite,
                START.plusSeconds(8)
        );

        assertEquals(TEN_MINUTES - 3_000, afterBlack.whiteRemainingMillis());
        assertEquals(TEN_MINUTES - 5_000, afterBlack.blackRemainingMillis());
        assertEquals(PieceColor.WHITE, afterBlack.runningColor());
        assertEquals(
                START.plusSeconds(8),
                afterBlack.turnStartedAt()
        );
        assertEquals(
                START.plusSeconds(605),
                GameClockCalculator.deadline(afterBlack)
        );
    }

    @Test
    void exactDeadlineExpiresAndCannotAcceptAnotherMove() {
        var state = GameClockCalculator.start(1_000, START);

        Instant justBefore = START.plusMillis(999);
        Instant deadline = START.plusMillis(1_000);

        assertFalse(
                GameClockCalculator.isExpired(state, justBefore)
        );
        assertEquals(
                1,
                GameClockCalculator.remainingMillis(
                        state,
                        PieceColor.WHITE,
                        justBefore
                )
        );
        assertDoesNotThrow(
                () -> GameClockCalculator.afterAcceptedMove(
                        state,
                        justBefore
                )
        );

        assertTrue(
                GameClockCalculator.isExpired(state, deadline)
        );
        assertThrows(
                IllegalStateException.class,
                () -> GameClockCalculator.afterAcceptedMove(
                        state,
                        deadline
                )
        );
    }

    @Test
    void expiryClampsToZeroEvenForVeryDistantTimestamps() {
        var state = GameClockCalculator.start(1_000, START);

        assertEquals(
                0,
                GameClockCalculator.remainingMillis(
                        state,
                        PieceColor.WHITE,
                        Instant.MAX
                )
        );
        assertTrue(
                GameClockCalculator.isExpired(state, Instant.MAX)
        );
    }

    @Test
    void backwardTimestampDoesNotIncreaseBalanceOrRewindTurnBoundary() {
        var state = GameClockCalculator.start(
                TEN_MINUTES,
                START
        );

        Instant earlier = START.minusSeconds(2);

        assertEquals(
                TEN_MINUTES,
                GameClockCalculator.remainingMillis(
                        state,
                        PieceColor.WHITE,
                        earlier
                )
        );

        var next = GameClockCalculator.afterAcceptedMove(
                state,
                earlier
        );

        assertEquals(TEN_MINUTES, next.whiteRemainingMillis());
        assertEquals(TEN_MINUTES, next.blackRemainingMillis());
        assertEquals(START, next.turnStartedAt());
        assertEquals(PieceColor.BLACK, next.runningColor());
    }

    @Test
    void blackExpiryUsesBlacksBalance() {
        var state = new GameClockState(
                100_000,
                2_000,
                PieceColor.BLACK,
                START
        );

        assertFalse(
                GameClockCalculator.isExpired(
                        state,
                        START.plusMillis(1_999)
                )
        );
        assertTrue(
                GameClockCalculator.isExpired(
                        state,
                        START.plusMillis(2_000)
                )
        );
        assertEquals(
                100_000,
                GameClockCalculator.remainingMillis(
                        state,
                        PieceColor.WHITE,
                        START.plusSeconds(10)
                )
        );
    }

    @Test
    void invalidInitialTimeAndNegativeBalancesAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GameClockCalculator.start(0, START)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GameClockCalculator.start(-1, START)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new GameClockState(
                        -1,
                        1_000,
                        PieceColor.WHITE,
                        START
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new GameClockState(
                        1_000,
                        -1,
                        PieceColor.BLACK,
                        START
                )
        );
    }
}