package com.chessapp.game;

import com.chessapp.game.clock.GameClockState;
import com.chessapp.game.dto.GameResponse;
import com.chessapp.game.entity.Game;
import com.chessapp.game.entity.GameResult;
import com.chessapp.game.entity.GameStatus;
import com.chessapp.game.entity.GameTerminationReason;
import com.chessapp.game.exception.GameClockExpiredException;
import com.chessapp.game.exception.StaleGameVersionException;
import com.chessapp.game.repository.GameRepository;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.PieceColor;
import com.chessapp.game.rules.model.Square;
import com.chessapp.game.service.GameService;
import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.service.PlayerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "app.game.clock.initial-time=1s",
        "app.game.clock.timeout-scan-enabled=false"
})
@ActiveProfiles("test")
class GameTimeoutIntegrationTest {

    private static final Instant START =
            Instant.parse("2026-11-02T08:00:00Z");

    @Autowired private GameService service;
    @Autowired private GameRepository repository;
    @Autowired private PlayerService playerService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private SimpMessagingTemplate outboundMessages;

    private final AtomicReference<Instant> currentTime =
            new AtomicReference<>(START);

    // Used only by the race test: each command observes a fixed timestamp
    // on its own thread, without changing the other command's timestamp.
    private final ThreadLocal<Instant> threadTime = new ThreadLocal<>();

    private List<PlayerResponse> players;
    private GameResponse game;

    @BeforeEach
    void setUp() {
        currentTime.set(START);

        when(clock.instant()).thenAnswer(invocation -> {
            Instant override = threadTime.get();
            return override != null ? override : currentTime.get();
        });

        players = transaction().execute(status -> List.of(
                createPlayer(),
                createPlayer()
        ));

        assertNotNull(players);
        game = service.createGame(white(), black());

        clearInvocations(outboundMessages);
    }

    @AfterEach
    void cleanUp() {
        threadTime.remove();

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

    @ParameterizedTest
    @EnumSource(PieceColor.class)
    void timeoutCommitsForEitherColorAndCannotCountTwice(
            PieceColor expiredColor
    ) {
        long expectedMoves = 0;
        long expectedVersion = 1;
        Instant deadline = START.plusSeconds(1);

        if (expiredColor == PieceColor.BLACK) {
            currentTime.set(START.plusMillis(100));
            service.submitMove(
                    game.id(), white(), game.version(), e4()
            );

            expectedMoves = 1;
            expectedVersion = 2;
            deadline = START.plusMillis(1_100);
        }

        // Process well after expiry: finishedAt must still be the deadline.
        currentTime.set(START.plusSeconds(5));

        assertTrue(service.expireGame(game.id()));

        var finished = reload();

        assertEquals(GameStatus.FINISHED, finished.getStatus());
        assertEquals(
                GameTerminationReason.TIMEOUT,
                finished.getTerminationReason()
        );
        assertEquals(deadline, finished.getFinishedAt());
        assertEquals(
                Long.valueOf(expectedVersion),
                finished.getVersion()
        );
        assertNull(finished.getClockRunningColor());
        assertNull(finished.getClockTurnStartedAt());
        assertEquals(expectedMoves, finished.getRecordedMoveCount());
        assertEquals(expectedMoves, moveCount());
        assertEquals(0L, reservationCount());
        assertEquals(1L, markerCount());

        assertEquals(
                Boolean.TRUE,
                jdbc.queryForObject("""
                        SELECT statistics_applied
                        FROM player_statistics_processed_games
                        WHERE game_id = ?
                        """, Boolean.class, game.id())
        );

        if (expiredColor == PieceColor.WHITE) {
            assertEquals(GameResult.BLACK_WIN, finished.getResult());
            assertEquals(
                    0L,
                    finished.getWhiteRemainingMillis().longValue()
            );
            assertEquals(
                    1_000L,
                    finished.getBlackRemainingMillis().longValue()
            );
            assertEquals(new Stats(1184, 0, 0, 1), stats(white()));
            assertEquals(new Stats(1216, 1, 0, 0), stats(black()));
        } else {
            assertEquals(GameResult.WHITE_WIN, finished.getResult());
            assertEquals(
                    900L,
                    finished.getWhiteRemainingMillis().longValue()
            );
            assertEquals(
                    0L,
                    finished.getBlackRemainingMillis().longValue()
            );
            assertEquals(new Stats(1216, 1, 0, 0), stats(white()));
            assertEquals(new Stats(1184, 0, 0, 1), stats(black()));
        }

        Stats whiteAfter = stats(white());
        Stats blackAfter = stats(black());

        clearInvocations(outboundMessages);

        assertFalse(service.expireGame(game.id()));

        assertEquals(finished.getVersion(), reload().getVersion());
        assertEquals(whiteAfter, stats(white()));
        assertEquals(blackAfter, stats(black()));
        assertEquals(1L, markerCount());
        verifyNoInteractions(outboundMessages);
    }

    @Test
    void candidateQueryIncludesExactDeadlineAndRechecksChangedClock() {
        currentTime.set(START.plusMillis(999));

        assertFalse(repository.findExpiredClockCandidates(
                currentTime.get(), 100
        ).contains(game.id()));

        currentTime.set(START.plusSeconds(1));

        assertTrue(repository.findExpiredClockCandidates(
                currentTime.get(), 100
        ).contains(game.id()));

        // Simulate a previously selected candidate whose current state
        // has since changed: White moved just before the old deadline.
        currentTime.set(START.plusMillis(999));

        service.submitMove(
                game.id(), white(), game.version(), e4()
        );

        currentTime.set(START.plusSeconds(1));

        // Black has spent only 1ms. The old candidate must not finish it.
        assertFalse(service.expireGame(game.id()));

        var active = reload();

        assertEquals(GameStatus.IN_PROGRESS, active.getStatus());
        assertEquals(
                PieceColor.BLACK,
                active.getClockRunningColor()
        );
        assertEquals(1L, active.getRecordedMoveCount());
        assertEquals(2L, reservationCount());
        assertEquals(0L, markerCount());
    }

    @Test
    void expiredMoveRejectsWithoutCompletingOrChangingClock() {
        GameClockState before = reload().runningClockState();

        currentTime.set(START.plusSeconds(1));

        assertThrows(
                GameClockExpiredException.class,
                () -> service.submitMove(
                        game.id(), white(), game.version(), e4()
                )
        );

        var unchanged = reload();

        assertEquals(GameStatus.IN_PROGRESS, unchanged.getStatus());
        assertEquals(game.version(), unchanged.getVersion());
        assertEquals(before, unchanged.runningClockState());
        assertEquals(game.currentFen(), unchanged.getCurrentFen());
        assertEquals(0L, moveCount());
        assertEquals(0L, markerCount());
        assertEquals(2L, reservationCount());
        assertEquals(new Stats(1200, 0, 0, 0), stats(white()));
        assertEquals(new Stats(1200, 0, 0, 0), stats(black()));
        verifyNoInteractions(outboundMessages);

        // The separate timeout transaction can then finish it normally.
        assertTrue(service.expireGame(game.id()));
        assertEquals(GameStatus.FINISHED, reload().getStatus());
    }

    @Test
    void ratingFailureRollsBackTimeoutAndAllowsSuccessfulRetry() {
        GameClockState before = reload().runningClockState();

        // White will lose on time. Force its loss counter to overflow.
        jdbc.update("""
                UPDATE player_statistics
                SET losses = ?
                WHERE player_id = ?
                """, Long.MAX_VALUE, white());

        currentTime.set(START.plusSeconds(2));

        assertThrows(
                ArithmeticException.class,
                () -> service.expireGame(game.id())
        );

        var unchanged = reload();

        assertEquals(GameStatus.IN_PROGRESS, unchanged.getStatus());
        assertEquals(game.version(), unchanged.getVersion());
        assertEquals(before, unchanged.runningClockState());
        assertNull(unchanged.getResult());
        assertNull(unchanged.getFinishedAt());
        assertEquals(0L, moveCount());
        assertEquals(0L, markerCount());
        assertEquals(2L, reservationCount());
        assertEquals(
                new Stats(1200, 0, 0, Long.MAX_VALUE),
                stats(white())
        );
        assertEquals(new Stats(1200, 0, 0, 0), stats(black()));
        verifyNoInteractions(outboundMessages);

        jdbc.update("""
                UPDATE player_statistics
                SET losses = 0
                WHERE player_id = ?
                """, white());

        assertTrue(service.expireGame(game.id()));

        assertEquals(GameStatus.FINISHED, reload().getStatus());
        assertEquals(1L, markerCount());
        assertEquals(0L, reservationCount());
        assertEquals(new Stats(1184, 0, 0, 1), stats(white()));
        assertEquals(new Stats(1216, 1, 0, 0), stats(black()));
    }

    @Test
    void moveAndTimeoutCannotBothCommitFromTheSameVersion()
            throws Exception {
        var bothLoaded = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var move = executor.submit(attempt(
                    START.plusMillis(999),
                    bothLoaded,
                    start,
                    () -> service.submitMove(
                            game.id(),
                            white(),
                            game.version(),
                            e4()
                    )
            ));

            var timeout = executor.submit(attempt(
                    START.plusSeconds(1),
                    bothLoaded,
                    start,
                    () -> assertTrue(service.expireGame(game.id()))
            ));

            assertTrue(bothLoaded.await(10, TimeUnit.SECONDS));
            start.countDown();

            boolean moveCommitted = move.get(15, TimeUnit.SECONDS);
            boolean timeoutCommitted = timeout.get(
                    15, TimeUnit.SECONDS
            );

            assertNotEquals(moveCommitted, timeoutCommitted);

            var persisted = reload();

            assertEquals(Long.valueOf(1), persisted.getVersion());

            if (moveCommitted) {
                assertEquals(
                        GameStatus.IN_PROGRESS,
                        persisted.getStatus()
                );
                assertEquals(1L, persisted.getRecordedMoveCount());
                assertEquals(1L, moveCount());
                assertEquals(0L, markerCount());
                assertEquals(2L, reservationCount());
                assertEquals(
                        PieceColor.BLACK,
                        persisted.getClockRunningColor()
                );
                assertEquals(
                        1L,
                        persisted.getWhiteRemainingMillis().longValue()
                );
                assertEquals(
                        1_000L,
                        persisted.getBlackRemainingMillis().longValue()
                );
                assertEquals(
                        new Stats(1200, 0, 0, 0),
                        stats(white())
                );
                assertEquals(
                        new Stats(1200, 0, 0, 0),
                        stats(black())
                );
            } else {
                assertEquals(
                        GameStatus.FINISHED,
                        persisted.getStatus()
                );
                assertEquals(
                        GameTerminationReason.TIMEOUT,
                        persisted.getTerminationReason()
                );
                assertEquals(GameResult.BLACK_WIN, persisted.getResult());
                assertEquals(
                        START.plusSeconds(1),
                        persisted.getFinishedAt()
                );
                assertEquals(game.currentFen(), persisted.getCurrentFen());
                assertEquals(0L, moveCount());
                assertEquals(1L, markerCount());
                assertEquals(0L, reservationCount());
                assertEquals(
                        0L,
                        persisted.getWhiteRemainingMillis().longValue()
                );
                assertEquals(
                        1_000L,
                        persisted.getBlackRemainingMillis().longValue()
                );
                assertNull(persisted.getClockRunningColor());
                assertNull(persisted.getClockTurnStartedAt());
                assertEquals(
                        new Stats(1184, 0, 0, 1),
                        stats(white())
                );
                assertEquals(
                        new Stats(1216, 1, 0, 0),
                        stats(black())
                );
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private Callable<Boolean> attempt(
            Instant observedTime,
            CountDownLatch bothLoaded,
            CountDownLatch start,
            Runnable command
    ) {
        return () -> {
            threadTime.set(observedTime);

            try {
                Boolean committed = transaction().execute(status -> {
                    // Both commands retain the same original entity/version
                    // in their separate transaction persistence contexts.
                    var loaded = repository.findById(game.id())
                            .orElseThrow();

                    assertEquals(game.version(), loaded.getVersion());

                    bothLoaded.countDown();

                    try {
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "Race start signal timed out"
                            );
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }

                    command.run();
                    return true;
                });

                return Boolean.TRUE.equals(committed);
            } catch (StaleGameVersionException exception) {
                return false;
            } finally {
                threadTime.remove();
            }
        };
    }

    private Game reload() {
        return repository.findById(game.id()).orElseThrow();
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

    private long moveCount() {
        return count("""
                SELECT count(*) FROM game_moves WHERE game_id = ?
                """);
    }

    private long markerCount() {
        return count("""
                SELECT count(*)
                FROM player_statistics_processed_games
                WHERE game_id = ?
                """);
    }

    private long reservationCount() {
        return count("""
                SELECT count(*)
                FROM active_game_players
                WHERE game_id = ?
                """);
    }

    private long count(String sql) {
        Long result = jdbc.queryForObject(
                sql, Long.class, game.id()
        );
        assertNotNull(result);
        return result;
    }

    private PlayerResponse createPlayer() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "");

        return playerService.createPlayer(
                "clock_" + suffix.substring(0, 16),
                suffix + "@example.com"
        );
    }

    private long white() {
        return players.getFirst().id();
    }

    private long black() {
        return players.get(1).id();
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private static Move e4() {
        return new Move(
                Square.fromAlgebraic("e2"),
                Square.fromAlgebraic("e4")
        );
    }

    private record Stats(
            int rating,
            long wins,
            long draws,
            long losses
    ) {
    }
}