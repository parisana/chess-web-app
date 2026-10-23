package com.chessapp.game;

import com.chessapp.game.exception.PlayerAlreadyInGameException;
import com.chessapp.game.service.GameService;
import com.chessapp.matchmaking.dto.QueueStatus;
import com.chessapp.matchmaking.dto.QueueStatusResponse;
import com.chessapp.matchmaking.repository.MatchmakingQueueRepository;
import com.chessapp.matchmaking.service.MatchmakingService;
import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.service.PlayerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ActiveGameIntegrationTest {

    @Autowired
    private GameService gameService;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private MatchmakingService matchmakingService;

    @Autowired
    private MatchmakingQueueRepository queueRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private List<PlayerResponse> players;

    @BeforeEach
    void setUp() {
        players = new TransactionTemplate(transactionManager)
                .execute(status -> List.of(
                        createPlayer(),
                        createPlayer(),
                        createPlayer()
                ));

        assertNotNull(players);
    }

    @AfterEach
    void cleanUp() {
        if (players == null) {
            return;
        }

        Object[] ids = players.stream()
                .map(PlayerResponse::id)
                .toArray();

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    jdbcTemplate.update("""
                            DELETE FROM matchmaking_queue
                            WHERE player_id IN (?, ?, ?)
                            """, ids);
                    jdbcTemplate.update("""
                            DELETE FROM active_game_players
                            WHERE player_id IN (?, ?, ?)
                            """, ids);

                    // Every game in this class uses only these fixtures.
                    jdbcTemplate.update("""
                            DELETE FROM games
                            WHERE white_player_id IN (?, ?, ?)
                            """, ids);

                    jdbcTemplate.update("""
                            DELETE FROM players
                            WHERE id IN (?, ?, ?)
                            """, ids);
                });
    }

    @Test
    void conflictingCreation_rollsBackGameAndPartialReservation() {
        Long available = players.get(0).id();
        Long occupied = players.get(1).id();
        Long opponent = players.get(2).id();

        var existing = gameService.createGame(occupied, opponent);

        assertEquals(
                existing.id(),
                gameService.findActiveGameId(occupied).orElseThrow()
        );
        assertEquals(
                existing.id(),
                gameService.findActiveGameId(opponent).orElseThrow()
        );

        // "available" is inserted first; "occupied" then conflicts.
        // The occupied player also changes color between the games.
        assertThrows(
                PlayerAlreadyInGameException.class,
                () -> gameService.createGame(available, occupied)
        );

        assertTrue(gameService.findActiveGameId(available).isEmpty());
        assertEquals(1L, fixtureGameCount());
        assertEquals(
                existing.id(),
                gameService.findActiveGameId(occupied).orElseThrow()
        );
    }

    @Test
    void concurrentCreation_withSharedPlayer_commitsOnlyOneGame()
            throws Exception {

        Long shared = players.get(0).id();
        Long second = players.get(1).id();
        Long third = players.get(2).id();

        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try {
            var first = executor.submit(
                    attempt(shared, second, ready, start)
            );
            var other = executor.submit(
                    attempt(third, shared, ready, start)
            );

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            boolean firstCreated = first.get(10, TimeUnit.SECONDS);
            boolean otherCreated = other.get(10, TimeUnit.SECONDS);

            assertNotEquals(firstCreated, otherCreated);
            assertEquals(1L, fixtureGameCount());

            Long gameId = gameService.findActiveGameId(shared)
                    .orElseThrow();

            Long winningOpponent = firstCreated ? second : third;
            Long losingOpponent = firstCreated ? third : second;

            assertEquals(
                    gameId,
                    gameService.findActiveGameId(winningOpponent)
                            .orElseThrow()
            );
            assertTrue(
                    gameService.findActiveGameId(losingOpponent).isEmpty()
            );
        } finally {
            start.countDown();
            executor.shutdownNow();

            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void joiningTwoPlayers_createsOneGameAndRemovesTheirEntries() {
        Long first = players.get(0).id();
        Long second = players.get(1).id();

        var waiting = matchmakingService.join(first);

        assertEquals(QueueStatus.WAITING, waiting.status());
        assertNotNull(waiting.enqueuedAt());
        assertNull(waiting.gameId());

        var matched = matchmakingService.join(second);

        assertEquals(QueueStatus.MATCHED, matched.status());
        assertNotNull(matched.gameId());
        assertNull(matched.enqueuedAt());

        assertEquals(matched, matchmakingService.status(first));
        assertEquals(matched, matchmakingService.status(second));

        assertTrue(queueRepository.findByPlayerId(first).isEmpty());
        assertTrue(queueRepository.findByPlayerId(second).isEmpty());
        assertEquals(1L, fixtureGameCount());

        assertEquals(
                matched.gameId(),
                gameService.findActiveGameId(first).orElseThrow()
        );
        assertEquals(
                matched.gameId(),
                gameService.findActiveGameId(second).orElseThrow()
        );

        // Rejoining must not create another game.
        assertEquals(matched, matchmakingService.join(first));
        assertEquals(1L, fixtureGameCount());

        // Leaving the queue must not cancel an established game.
        matchmakingService.leave(first);
        assertEquals(matched, matchmakingService.status(first));
    }

    @Test
    void pairing_participatesInOuterTransactionAndRollsBackTogether() {
        Long first = players.get(0).id();
        Long second = players.get(1).id();

        var originalWaiting = matchmakingService.join(first);

        assertThrows(
                IllegalStateException.class,
                () -> new TransactionTemplate(transactionManager)
                        .executeWithoutResult(status -> {
                            var matched = matchmakingService.join(second);

                            assertEquals(
                                    QueueStatus.MATCHED,
                                    matched.status()
                            );

                            throw new IllegalStateException(
                                    "Simulated later transaction failure"
                            );
                        })
        );

        assertEquals(0L, fixtureGameCount());
        assertTrue(gameService.findActiveGameId(first).isEmpty());
        assertTrue(gameService.findActiveGameId(second).isEmpty());

        assertEquals(originalWaiting, matchmakingService.status(first));
        assertEquals(
                QueueStatus.NOT_QUEUED,
                matchmakingService.status(second).status()
        );
    }

    @Test
    void concurrentJoins_pairPlayersWithoutCreatingDuplicateGames()
            throws Exception {

        Long firstPlayerId = players.get(0).id();
        Long secondPlayerId = players.get(1).id();

        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try {
            var first = executor.submit(
                    joinAttempt(firstPlayerId, ready, start)
            );
            var second = executor.submit(
                    joinAttempt(secondPlayerId, ready, start)
            );

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            var firstStatus = matchmakingService.status(firstPlayerId);
            var secondStatus = matchmakingService.status(secondPlayerId);

            assertEquals(QueueStatus.MATCHED, firstStatus.status());
            assertNotNull(firstStatus.gameId());
            assertEquals(firstStatus, secondStatus);
            assertEquals(1L, fixtureGameCount());

            assertTrue(
                    queueRepository.findByPlayerId(firstPlayerId).isEmpty()
            );
            assertTrue(
                    queueRepository.findByPlayerId(secondPlayerId).isEmpty()
            );
        } finally {
            start.countDown();
            executor.shutdownNow();

            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Callable<QueueStatusResponse> joinAttempt(
            Long playerId,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return () -> {
            ready.countDown();

            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Start signal timed out");
            }

            return matchmakingService.join(playerId);
        };
    }

    private Callable<Boolean> attempt(
            Long whitePlayerId,
            Long blackPlayerId,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return () -> {
            ready.countDown();

            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Start signal timed out");
            }

            try {
                gameService.createGame(whitePlayerId, blackPlayerId);
                return true;
            } catch (PlayerAlreadyInGameException exception) {
                return false;
            }
        };
    }

    private long fixtureGameCount() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM games
                WHERE white_player_id IN (?, ?, ?)
                """,
                Long.class,
                players.get(0).id(),
                players.get(1).id(),
                players.get(2).id()
        );

        assertNotNull(count);
        return count;
    }

    private PlayerResponse createPlayer() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16);

        return playerService.createPlayer(
                "active_" + suffix,
                suffix + "@example.com"
        );
    }
}