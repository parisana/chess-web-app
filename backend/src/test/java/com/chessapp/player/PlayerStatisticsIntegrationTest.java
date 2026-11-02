package com.chessapp.player;

import com.chessapp.game.dto.GameResponse;
import com.chessapp.game.entity.GameResult;
import com.chessapp.game.event.GameFinishedEvent;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.Square;
import com.chessapp.game.service.GameService;
import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.service.PlayerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 11/1/26
 */
@SpringBootTest
@ActiveProfiles("test")
class PlayerStatisticsIntegrationTest {

    @Autowired private PlayerService playerService;
    @Autowired private GameService gameService;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private List<PlayerResponse> players;
    private GameResponse game;

    @BeforeEach
    void setUp() {
        players = transaction().execute(status -> List.of(
                createPlayer(),
                createPlayer()
        ));

        assertNotNull(players);
        game = gameService.createGame(white(), black());
    }

    @AfterEach
    void cleanUp() {
        if (players == null) {
            return;
        }

        transaction().executeWithoutResult(status -> {
            jdbc.update("""
                    DELETE FROM player_statistics_processed_games
                    WHERE game_id IN (
                        SELECT id FROM games
                        WHERE white_player_id IN (?, ?)
                    )
                    """, white(), black());

            jdbc.update("""
                    DELETE FROM game_moves
                    WHERE game_id IN (
                        SELECT id FROM games
                        WHERE white_player_id IN (?, ?)
                    )
                    """, white(), black());

            jdbc.update("""
                    DELETE FROM active_game_players
                    WHERE player_id IN (?, ?)
                    """, white(), black());

            jdbc.update("""
                    DELETE FROM games
                    WHERE white_player_id IN (?, ?)
                    """, white(), black());

            jdbc.update("""
                    DELETE FROM player_statistics
                    WHERE player_id IN (?, ?)
                    """, white(), black());

            jdbc.update("""
                    DELETE FROM players
                    WHERE id IN (?, ?)
                    """, white(), black());
        });
    }

    @Test
    void playerCreationInitializesPersistedStatistics() {
        assertEquals(new Stats(1200, 0, 0, 0), stats(white()));
        assertEquals(new Stats(1200, 0, 0, 0), stats(black()));
    }

    @ParameterizedTest
    @EnumSource(GameResult.class)
    void completionCommitsBothPlayersAndOneMarker(GameResult result) {
        GameResponse finished = finish(result);

        assertEquals(result, finished.result());
        assertEquals(1L, markerCount());

        assertStatisticsFor(result);

        assertEquals(0L, reservationCount());
    }

    @Test
    void repeatedCompletionEventDoesNotCountTheGameTwice() {
        finish(GameResult.WHITE_WIN);

        Stats whiteBefore = stats(white());
        Stats blackBefore = stats(black());

        transaction().executeWithoutResult(status ->
                events.publishEvent(new GameFinishedEvent(
                        game.id(),
                        white(),
                        black(),
                        GameResult.WHITE_WIN
                ))
        );

        assertEquals(whiteBefore, stats(white()));
        assertEquals(blackBefore, stats(black()));
        assertEquals(1L, markerCount());
    }

    @Test
    void ratingFailureRollsBackCompletionMarkerAndBothStatistics() {
        // Deliberately force Black's loss counter to overflow.
        // White is updated in memory first, so rollback must protect both.
        jdbc.update("""
                UPDATE player_statistics
                SET losses = ?
                WHERE player_id = ?
                """, Long.MAX_VALUE, black());

        assertThrows(
                ArithmeticException.class,
                () -> gameService.resign(
                        game.id(),
                        black(),
                        game.version()
                )
        );

        assertEquals(
                "IN_PROGRESS",
                jdbc.queryForObject(
                        "SELECT status FROM games WHERE id = ?",
                        String.class,
                        game.id()
                )
        );
        assertEquals(
                game.version().longValue(),
                jdbc.queryForObject(
                        "SELECT version FROM games WHERE id = ?",
                        Long.class,
                        game.id()
                ).longValue()
        );

        assertEquals(new Stats(1200, 0, 0, 0), stats(white()));
        assertEquals(
                new Stats(1200, 0, 0, Long.MAX_VALUE),
                stats(black())
        );
        assertEquals(0L, markerCount());
        assertEquals(2L, reservationCount());

        // Remove the injected fault and prove the same game can finish.
        jdbc.update("""
                UPDATE player_statistics
                SET losses = 0
                WHERE player_id = ?
                """, black());

        finish(GameResult.WHITE_WIN);

        assertEquals(1L, markerCount());
        assertEquals(new Stats(1216, 1, 0, 0), stats(white()));
        assertEquals(new Stats(1184, 0, 0, 1), stats(black()));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // White checkmates Black.
            "7k/8/5KQ1/8/8/8/8/8 w - - 4 20"
                    + " | g6 | g7 | true | WHITE_WIN",

            // Black checkmates White.
            "8/8/8/8/8/5kq1/8/7K b - - 4 20"
                    + " | g3 | g2 | false | BLACK_WIN",

            // White stalemates Black.
            "7k/8/6K1/5Q2/8/8/8/8 w - - 4 20"
                    + " | f5 | f7 | true | DRAW",

            // Black stalemates White.
            "8/8/8/8/5q2/6k1/8/7K b - - 4 20"
                    + " | f4 | f2 | false | DRAW"
    })
    void terminalMoveCommitsHistoryAndStatistics(
            String fen,
            String from,
            String to,
            boolean whiteMoves,
            GameResult expectedResult
    ) {
        seedFen(fen);

        gameService.submitMove(
                game.id(),
                whiteMoves ? white() : black(),
                game.version(),
                new Move(Square.fromAlgebraic(from), Square.fromAlgebraic(to))
        );

        assertEquals(
                expectedResult,
                gameService.getGame(game.id(), white()).result()
        );

        assertEquals(1L, markerCount());
        assertEquals(0L, reservationCount());
        assertEquals(
                1L,
                jdbc.queryForObject("""
                    SELECT count(*)
                    FROM game_moves
                    WHERE game_id = ?
                    """, Long.class, game.id()).longValue()
        );

        assertStatisticsFor(expectedResult);
    }

    @Test
    void ratingFailureRollsBackTerminalMoveAndItsHistory() {
        String beforeMate = "7k/8/5KQ1/8/8/8/8/8 w - - 4 20";
        seedFen(beforeMate);

        // White's mating move would increment Black's loss counter.
        jdbc.update("""
            UPDATE player_statistics
            SET losses = ?
            WHERE player_id = ?
            """, Long.MAX_VALUE, black());

        assertThrows(
                ArithmeticException.class,
                () -> gameService.submitMove(
                        game.id(),
                        white(),
                        game.version(),
                        new Move(
                                Square.fromAlgebraic("g6"),
                                Square.fromAlgebraic("g7")
                        )
                )
        );

        var persisted = gameService.getGame(game.id(), white());

        assertEquals(beforeMate, persisted.currentFen());
        assertEquals(game.version(), persisted.version());
        assertNull(persisted.result());
        assertNull(persisted.finishedAt());

        assertEquals(
                0L,
                jdbc.queryForObject("""
                    SELECT recorded_move_count
                    FROM games
                    WHERE id = ?
                    """, Long.class, game.id()).longValue()
        );
        assertEquals(
                0L,
                jdbc.queryForObject("""
                    SELECT count(*)
                    FROM game_moves
                    WHERE game_id = ?
                    """, Long.class, game.id()).longValue()
        );

        assertEquals(0L, markerCount());
        assertEquals(2L, reservationCount());
        assertEquals(new Stats(1200, 0, 0, 0), stats(white()));
        assertEquals(
                new Stats(1200, 0, 0, Long.MAX_VALUE),
                stats(black())
        );
    }

    @Test
    void concurrentCompletionEventsApplyStatisticsOnce() throws Exception {
        // Construct a persisted, finished-but-unprocessed fixture.
        // This deliberately bypasses application completion to isolate
        // concurrent event handling; normal completion is tested separately.
        transaction().executeWithoutResult(status -> {
            assertEquals(
                    1,
                    jdbc.update("""
                        UPDATE games
                        SET status = 'FINISHED',
                            result = 'WHITE_WIN',
                            termination_reason = 'RESIGNATION',
                            finished_at = CURRENT_TIMESTAMP,
                            version = version + 1
                        WHERE id = ?
                        """, game.id())
            );

            assertEquals(
                    2,
                    jdbc.update("""
                        DELETE FROM active_game_players
                        WHERE game_id = ?
                        """, game.id())
            );
        });

        var event = new GameFinishedEvent(
                game.id(),
                white(),
                black(),
                GameResult.WHITE_WIN
        );

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        Runnable deliver = () -> {
            ready.countDown();

            try {
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Delivery start timed out");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Delivery interrupted", exception);
            }

            // Each worker has its own transaction and persistence context.
            var tx = transaction();
            tx.setTimeout(10);

            tx.executeWithoutResult(status -> events.publishEvent(event));
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(deliver);
            var second = executor.submit(deliver);

            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }

            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        }

        assertEquals(1L, markerCount());
        assertStatisticsFor(GameResult.WHITE_WIN);
    }

    @Test
    void ordinaryMoveAndDrawNegotiationDoNotUpdateStatistics() {
        var moved = gameService.submitMove(
                game.id(),
                white(),
                game.version(),
                new Move(
                        Square.fromAlgebraic("e2"),
                        Square.fromAlgebraic("e4")
                )
        );

        var offered = gameService.offerDraw(
                game.id(),
                white(),
                moved.version()
        );

        var declined = gameService.declineDraw(
                game.id(),
                black(),
                offered.version()
        );

        var offeredAgain = gameService.offerDraw(
                game.id(),
                black(),
                declined.version()
        );

        gameService.withdrawDraw(
                game.id(),
                black(),
                offeredAgain.version()
        );

        assertEquals(new Stats(1200, 0, 0, 0), stats(white()));
        assertEquals(new Stats(1200, 0, 0, 0), stats(black()));
        assertEquals(0L, markerCount());
        assertEquals(2L, reservationCount());

        assertNull(
                gameService.getGame(game.id(), white()).result()
        );
    }

    @Test
    void outerRollbackUndoesSuccessfulCompletionAndStatistics() {
        var failure = new IllegalStateException(
                "Simulated failure after completion processing"
        );

        var thrown = assertThrows(
                IllegalStateException.class,
                () -> transaction().executeWithoutResult(status -> {
                    gameService.resign(
                            game.id(),
                            black(),
                            game.version()
                    );

                    // The listener has successfully updated and flushed both
                    // statistics rows, but this outer transaction has not committed.
                    assertStatisticsFor(GameResult.WHITE_WIN);
                    assertEquals(1L, markerCount());
                    assertEquals(0L, reservationCount());

                    throw failure;
                })
        );

        assertSame(failure, thrown);

        // Fresh reads after rollback must observe the original committed state.
        var persisted = gameService.getGame(game.id(), white());

        assertEquals(game.version(), persisted.version());
        assertNull(persisted.result());
        assertNull(persisted.finishedAt());

        assertEquals(new Stats(1200, 0, 0, 0), stats(white()));
        assertEquals(new Stats(1200, 0, 0, 0), stats(black()));
        assertEquals(0L, markerCount());
        assertEquals(2L, reservationCount());
    }

    private GameResponse finish(GameResult result) {
        return switch (result) {
            case WHITE_WIN -> gameService.resign(
                    game.id(), black(), game.version()
            );
            case BLACK_WIN -> gameService.resign(
                    game.id(), white(), game.version()
            );
            case DRAW -> {
                var offered = gameService.offerDraw(
                        game.id(), white(), game.version()
                );
                yield gameService.acceptDraw(
                        game.id(), black(), offered.version()
                );
            }
        };
    }

    private Stats stats(long playerId) {
        return jdbc.queryForObject("""
                SELECT rating, wins, draws, losses
                FROM player_statistics
                WHERE player_id = ?
                """,
                (row, index) -> new Stats(
                        row.getInt("rating"),
                        row.getLong("wins"),
                        row.getLong("draws"),
                        row.getLong("losses")
                ),
                playerId
        );
    }

    private long markerCount() {
        return jdbc.queryForObject("""
                SELECT count(*)
                FROM player_statistics_processed_games
                WHERE game_id = ?
                """, Long.class, game.id());
    }

    private long reservationCount() {
        return jdbc.queryForObject("""
                SELECT count(*)
                FROM active_game_players
                WHERE game_id = ?
                """, Long.class, game.id());
    }

    private PlayerResponse createPlayer() {
        String suffix = UUID.randomUUID().toString().replace("-", "");

        return playerService.createPlayer(
                "p_" + suffix.substring(0, 20),
                suffix + "@example.com"
        );
    }

    private long white() {
        return players.get(0).id();
    }

    private long black() {
        return players.get(1).id();
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private record Stats(int rating, long wins, long draws, long losses) {
    }

    private void seedFen(String fen) {
        // Controlled setup before any moves. Keep the history baseline aligned.
        assertEquals(
                1,
                jdbc.update("""
                    UPDATE games
                    SET current_fen = ?,
                        history_start_fen = ?
                    WHERE id = ?
                    """, fen, fen, game.id())
        );
    }

    private void assertStatisticsFor(GameResult result) {
        switch (result) {
            case WHITE_WIN -> {
                assertEquals(new Stats(1216, 1, 0, 0), stats(white()));
                assertEquals(new Stats(1184, 0, 0, 1), stats(black()));
            }
            case BLACK_WIN -> {
                assertEquals(new Stats(1184, 0, 0, 1), stats(white()));
                assertEquals(new Stats(1216, 1, 0, 0), stats(black()));
            }
            case DRAW -> {
                assertEquals(new Stats(1200, 0, 1, 0), stats(white()));
                assertEquals(new Stats(1200, 0, 1, 0), stats(black()));
            }
        }
    }
}
