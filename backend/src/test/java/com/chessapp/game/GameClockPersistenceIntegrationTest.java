package com.chessapp.game;

import com.chessapp.game.clock.GameClockState;
import com.chessapp.game.entity.Game;
import com.chessapp.game.entity.GameResult;
import com.chessapp.game.entity.GameTerminationReason;
import com.chessapp.game.rules.model.PieceColor;
import com.chessapp.player.service.PlayerService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
/**
 * @author pari on 11/2/26
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GameClockPersistenceIntegrationTest {

    private static final String INITIAL =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/"
                    + "RNBQKBNR w KQkq - 0 1";

    private static final Instant START =
            Instant.parse("2026-11-02T08:00:00Z");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlayerService playerService;

    private Long whiteId;
    private Long blackId;

    @BeforeEach
    void createPlayers() {
        whiteId = createPlayer();
        blackId = createPlayer();
    }

    @Test
    void runningClockSurvivesFlushClearAndReload() {
        var game = new Game(whiteId, blackId, INITIAL);
        game.startClock(600_000, START);
        entityManager.persist(game);

        var loaded = reload(game);

        assertEquals(
                new GameClockState(
                        600_000,
                        600_000,
                        PieceColor.WHITE,
                        START
                ),
                loaded.runningClockState()
        );

        // Keep position and running color consistent in this fixture.
        loaded.updatePosition(
                "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/"
                        + "RNBQKBNR b KQkq e3 0 1"
        );
        loaded.advanceClockAfterAcceptedMove(
                START.plusSeconds(3)
        );

        var afterMove = reload(loaded);

        assertEquals(
                new GameClockState(
                        597_000,
                        600_000,
                        PieceColor.BLACK,
                        START.plusSeconds(3)
                ),
                afterMove.runningClockState()
        );
    }

    @Test
    void completionPersistsFrozenBalancesWithoutRunningMetadata() {
        var game = new Game(whiteId, blackId, INITIAL);
        game.startClock(600_000, START);
        entityManager.persist(game);

        var loaded = reload(game);

        loaded.finish(
                GameResult.BLACK_WIN,
                GameTerminationReason.RESIGNATION,
                START.plusSeconds(7)
        );

        var finished = reload(loaded);

        assertAll(
                () -> assertTrue(finished.hasClock()),
                () -> assertEquals(
                        593_000L,
                        finished.getWhiteRemainingMillis().longValue()
                ),
                () -> assertEquals(
                        600_000L,
                        finished.getBlackRemainingMillis().longValue()
                ),
                () -> assertNull(finished.getClockRunningColor()),
                () -> assertNull(finished.getClockTurnStartedAt()),
                () -> assertEquals(
                        START.plusSeconds(7),
                        finished.getFinishedAt()
                )
        );
    }

    @Test
    void untimedGameRetainsNullClockColumns() {
        var game = new Game(whiteId, blackId, INITIAL);
        entityManager.persist(game);

        var loaded = reload(game);

        assertFalse(loaded.hasClock());
        assertNull(loaded.getWhiteRemainingMillis());
        assertNull(loaded.getBlackRemainingMillis());
        assertNull(loaded.getClockRunningColor());
        assertNull(loaded.getClockTurnStartedAt());
    }

    private Game reload(Game game) {
        entityManager.flush();
        Long id = game.getId();

        // Force a database read rather than returning the managed entity.
        entityManager.clear();

        return entityManager.find(Game.class, id);
    }

    private Long createPlayer() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16);

        return playerService.createPlayer(
                "clock_" + suffix,
                suffix + "@example.com"
        ).id();
    }
}
