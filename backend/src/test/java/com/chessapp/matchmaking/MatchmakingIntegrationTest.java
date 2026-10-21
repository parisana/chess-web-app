package com.chessapp.matchmaking;

import com.chessapp.matchmaking.dto.QueueStatus;
import com.chessapp.matchmaking.exception.MatchmakingPlayerUnavailableException;
import com.chessapp.matchmaking.repository.MatchmakingQueueRepository;
import com.chessapp.matchmaking.service.MatchmakingService;
import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.repository.PlayerRepository;
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

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/21/26
 */
@SpringBootTest
@ActiveProfiles("test")
class MatchmakingIntegrationTest {

    @Autowired
    private MatchmakingService matchmakingService;

    @Autowired
    private MatchmakingQueueRepository queueRepository;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TransactionTemplate transactions;
    private PlayerResponse player;

    @BeforeEach
    void setUp() {
        transactions = new TransactionTemplate(transactionManager);

        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16);

        player = transactions.execute(status ->
                playerService.createPlayer(
                        "queue_" + suffix,
                        suffix + "@example.com"
                )
        );

        assertNotNull(player);
    }

    @AfterEach
    void cleanUp() {
        if (player == null) {
            return;
        }

        transactions.executeWithoutResult(status -> {
            queueRepository.deleteEntryByPlayerId(player.id());
            playerRepository.deleteById(player.id());
        });
    }

    private long queueEntryCount() {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM matchmaking_queue
                WHERE player_id = ?
                """,
                Long.class,
                player.id()
        );
        assertNotNull(count);
        return count;
    }

    @Test
    void join_createsPersistedQueueEntry() {
        var initial = matchmakingService.status(player.id());

        assertEquals(QueueStatus.NOT_QUEUED, initial.status());
        assertNull(initial.enqueuedAt());
        assertEquals(0L, queueEntryCount());

        var joined = matchmakingService.join(player.id());

        assertEquals(QueueStatus.WAITING, joined.status());
        assertNotNull(joined.enqueuedAt());
        assertEquals(1L, queueEntryCount());

        var persisted = queueRepository.findByPlayerId(player.id())
                .orElseThrow();

        assertEquals(joined.enqueuedAt(), persisted.getEnqueuedAt());

        var current = matchmakingService.status(player.id());

        assertEquals(joined, current);
    }

    @Test
    void repeatedJoin_preservesEntryAndEnqueueTime() {
        var first = matchmakingService.join(player.id());

        var originalEntry = queueRepository.findByPlayerId(player.id())
                .orElseThrow();

        var second = matchmakingService.join(player.id());

        var currentEntry = queueRepository.findByPlayerId(player.id())
                .orElseThrow();

        assertEquals(first, second);
        assertEquals(originalEntry.getId(), currentEntry.getId());
        assertEquals(
                originalEntry.getEnqueuedAt(),
                currentEntry.getEnqueuedAt()
        );
        assertEquals(1L, queueEntryCount());
    }

    @Test
    void leave_removesEntryAndAllowsRepeatedLeave() {
        matchmakingService.join(player.id());

        matchmakingService.leave(player.id());

        assertEquals(0L, queueEntryCount());

        var current = matchmakingService.status(player.id());

        assertEquals(QueueStatus.NOT_QUEUED, current.status());
        assertNull(current.enqueuedAt());

        assertDoesNotThrow(() ->
                matchmakingService.leave(player.id())
        );

        assertEquals(0L, queueEntryCount());
    }

    @Test
    void operations_rejectPlayerThatNoLongerExists() {
        transactions.executeWithoutResult(status ->
                playerRepository.deleteById(player.id())
        );

        assertThrows(
                MatchmakingPlayerUnavailableException.class,
                () -> matchmakingService.join(player.id())
        );

        assertThrows(
                MatchmakingPlayerUnavailableException.class,
                () -> matchmakingService.status(player.id())
        );

        assertThrows(
                MatchmakingPlayerUnavailableException.class,
                () -> matchmakingService.leave(player.id())
        );

        assertEquals(0L, queueEntryCount());
    }

    @Test
    void concurrentJoins_createOneEntryAndReturnSameEnqueueTime()
            throws Exception {

        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try {
            var first = executor.submit(() -> {
                ready.countDown();

                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Start signal timed out");
                }

                return matchmakingService.join(player.id());
            });

            var second = executor.submit(() -> {
                ready.countDown();

                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Start signal timed out");
                }

                return matchmakingService.join(player.id());
            });

            assertTrue(
                    ready.await(5, TimeUnit.SECONDS),
                    "Both workers should be ready"
            );

            start.countDown();

            var firstResult = first.get(10, TimeUnit.SECONDS);
            var secondResult = second.get(10, TimeUnit.SECONDS);

            assertEquals(QueueStatus.WAITING, firstResult.status());
            assertEquals(firstResult, secondResult);
            assertEquals(1L, queueEntryCount());
        } finally {
            start.countDown();
            executor.shutdownNow();

            assertTrue(
                    executor.awaitTermination(5, TimeUnit.SECONDS),
                    "Queue workers should terminate before fixture cleanup"
            );
        }
    }
}
