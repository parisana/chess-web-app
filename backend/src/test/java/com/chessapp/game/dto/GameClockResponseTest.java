package com.chessapp.game.dto;

import com.chessapp.game.entity.Game;
import com.chessapp.game.entity.GameResult;
import com.chessapp.game.entity.GameTerminationReason;
import com.chessapp.game.rules.model.PieceColor;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 11/3/26
 */
class GameClockResponseTest {

    private static final Instant START =
            Instant.parse("2026-11-02T08:00:00Z");

    private static Game newGame() {
        return new Game(
                1L,
                2L,
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/"
                        + "RNBQKBNR w KQkq - 0 1"
        );
    }

    @Test
    void untimedGameHasNoClockResponse() {
        assertNull(GameClockResponse.from(newGame(), START));
    }

    @Test
    void projectsBalancesWithoutChangingStoredState() {
        var game = newGame();
        game.startClock(10_000, START);

        var before = game.runningClockState();
        Instant capturedAt = START.plusSeconds(3);

        var response = GameClockResponse.from(game, capturedAt);

        assertNotNull(response);
        assertEquals(7_000L, response.whiteRemainingMillis());
        assertEquals(10_000L, response.blackRemainingMillis());
        assertEquals(PieceColor.WHITE, response.runningColor());
        assertEquals(capturedAt, response.calculatedAt());
        assertEquals(before, game.runningClockState());
    }

    @Test
    void finishedBalancesRemainFrozenAtLaterReadTimes() {
        var game = newGame();
        game.startClock(10_000, START);

        game.finish(
                GameResult.BLACK_WIN,
                GameTerminationReason.RESIGNATION,
                START.plusSeconds(3)
        );

        var response = GameClockResponse.from(
                game,
                START.plusSeconds(100)
        );

        assertNotNull(response);
        assertEquals(7_000L, response.whiteRemainingMillis());
        assertEquals(10_000L, response.blackRemainingMillis());
        assertNull(response.runningColor());
    }
}