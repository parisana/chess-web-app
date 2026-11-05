package com.chessapp.game;

import com.chessapp.game.entity.Game;
import com.chessapp.game.entity.GameResult;
import com.chessapp.game.entity.GameTerminationReason;
import com.chessapp.game.service.GameQueryService;
import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.service.PlayerService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author pari on 11/5/26
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@ActiveProfiles({"test", "sql"})
@Transactional
class PlayedGamesQueryEfficiencyIntegrationTest {

    private static final Logger log =
            LoggerFactory.getLogger(PlayedGamesQueryEfficiencyIntegrationTest.class);

    private static final String INITIAL =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                    + " w KQkq - 0 1";

    @Autowired private GameQueryService gameQueryService;
    @Autowired private PlayerService playerService;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void listingQueryCountDoesNotGrowWithTheNumberOfGames() {
        var subject = createPlayer();

        addFinishedGame(subject, 0);
        long smallListingCount = measureListing(subject.id(), 1, false);

        // Different opponents prevent repeated IDs from hiding extra
        // lookups behind the persistence context's first-level cache.
        for (int index = 1; index < 25; index++) {
            addFinishedGame(subject, index);
        }

        long fullListingCount = measureListing(subject.id(), 20, true);

        assertEquals(1L, smallListingCount);
        assertEquals(1L, fullListingCount);
    }

    private long measureListing(long subjectId, int expectedRows, boolean expectedMore) {
        // Finish fixture writes and remove managed entities before measuring.
        entityManager.flush();
        entityManager.clear();

        var statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();

        log.info("BEGIN measured played-games listing");

        long before = statistics.getPrepareStatementCount();

        var result = gameQueryService.getPlayedGames(subjectId, 0, 20);

        long count = statistics.getPrepareStatementCount() - before;

        log.info("Returned games: {}; prepared SQL statements: {}", result.games().size(), count);
        log.info("END measured played-games listing");

        assertEquals(expectedRows, result.games().size());
        assertEquals(expectedMore, result.hasNext());

        return count;
    }

    private void addFinishedGame(PlayerResponse subject, int index) {
        var opponent = createPlayer();

        // Exercise both colour perspectives.
        var white = index % 2 == 0 ? subject : opponent;
        var black = index % 2 == 0 ? opponent : subject;

        var game = new Game(white.id(), black.id(), INITIAL);

        game.captureParticipantDetails(
                white.username(),
                black.username(),
                1200,
                1200
        );

        game.finish(
                GameResult.DRAW,
                GameTerminationReason.DRAW_AGREEMENT,
                Instant.parse("2026-11-05T08:00:00Z").plusSeconds(index)
        );

        // Controlled read-test fixture: no command/rating processing.
        entityManager.persist(game);
    }

    private PlayerResponse createPlayer() {
        String suffix = UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12);

        return playerService.createPlayer(
                "john_doe_" + suffix,
                suffix + "@example.com"
        );
    }
}
