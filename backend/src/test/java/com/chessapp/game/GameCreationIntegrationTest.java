package com.chessapp.game;

import com.chessapp.game.entity.Game;
import com.chessapp.game.entity.GameStatus;
import com.chessapp.game.repository.GameRepository;
import com.chessapp.game.service.GameQueryService;
import com.chessapp.game.service.GameCommandService;
import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.repository.PlayerStatisticsRepository;
import com.chessapp.player.service.PlayerService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GameCreationIntegrationTest {

    @Autowired
    private GameCommandService gameCommandService;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameQueryService gameQueryService;

    @Autowired
    private PlayerStatisticsRepository playerStatisticsRepository;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private EntityManager entityManager;

    private PlayerResponse white;
    private PlayerResponse black;
    private static final String EXPECTED_INITIAL_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                    + " w KQkq - 0 1";

    @BeforeEach
    void setUp() {
        white = createPlayer();
        black = createPlayer();
    }

    @Test
    void createGame_persistsInitialState() {
        var response = gameCommandService.createGame(
                white.id(),
                black.id()
        );

        // flush() sends pending changes to PostgreSQL, and clear() makes the subsequent lookup reload the entity.
        // This verifies persistence mappings rather than merely reading the same Java object from JPA’s persistence context.
        entityManager.flush();
        entityManager.clear();

        var persisted = gameRepository.findById(response.id())
                .orElseThrow();

        assertAll(
                () -> assertNotNull(response.id()),
                () -> assertEquals(
                        white.id(), response.whitePlayerId()
                ),
                () -> assertEquals(
                        black.id(), response.blackPlayerId()
                ),
                () -> assertEquals(
                        GameStatus.IN_PROGRESS, response.status()
                ),
                () -> assertEquals(
                        EXPECTED_INITIAL_FEN, response.currentFen()
                ),
                () -> assertNotNull(response.createdAt()),
                () -> assertEquals(
                        response.whitePlayerId(),
                        persisted.getWhitePlayerId()
                ),
                () -> assertEquals(
                        response.blackPlayerId(),
                        persisted.getBlackPlayerId()
                ),
                () -> assertEquals(
                        response.status(), persisted.getStatus()
                ),
                () -> assertEquals(
                        response.currentFen(), persisted.getCurrentFen()
                ),
                () -> assertEquals(
                        Long.valueOf(0), persisted.getVersion()
                ),
                () -> assertNotNull(persisted.getCreatedAt()),
                () -> assertNotNull(persisted.getUpdatedAt())
        );
    }

    @Test
    void createGame_rejectsSamePlayerForBothColors() {
        assertThrows(
                IllegalArgumentException.class,
                () -> gameCommandService.createGame(
                        white.id(),
                        white.id()
                )
        );
    }

    @Test
    void createGame_rejectsPlayerThatNoLongerExists() {
        Long deletedPlayerId = black.id();
        playerStatisticsRepository.deleteById(deletedPlayerId);
        playerStatisticsRepository.flush();

        entityManager.remove(
                entityManager.getReference(
                        com.chessapp.player.entity.Player.class,
                        deletedPlayerId
                )
        );
        entityManager.flush();
        entityManager.clear();

        assertThrows(
                IllegalArgumentException.class,
                () -> gameCommandService.createGame(
                        white.id(),
                        deletedPlayerId
                )
        );
    }

    @Test
    void participantSnapshotsSurviveProfileChangesAndCompletion() {
        var created = gameCommandService.createGame(white.id(), black.id());

        int whiteStartingRating = playerStatisticsRepository
                .findById(white.id()).orElseThrow().getRating();
        int blackStartingRating = playerStatisticsRepository
                .findById(black.id()).orElseThrow().getRating();

        assertEquals(white.username(), created.whiteUsernameAtStart());
        assertEquals(black.username(), created.blackUsernameAtStart());
        assertEquals(
                Integer.valueOf(whiteStartingRating),
                created.whiteRatingAtStart()
        );
        assertEquals(
                Integer.valueOf(blackStartingRating),
                created.blackRatingAtStart()
        );

        String suffix = UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12);

        playerService.updateUsername(white.id(), "renamed_w_" + suffix)
                .orElseThrow();
        playerService.updateUsername(black.id(), "renamed_b_" + suffix)
                .orElseThrow();

        // Completion changes the actual ratings through the existing listener.
        var finished = gameCommandService.resign(
                created.id(),
                white.id(),
                created.version()
        );

        assertEquals(
                created.whiteRatingAtStart(),
                finished.whiteRatingAtStart()
        );

        entityManager.flush();
        entityManager.clear();

        var reloaded = gameQueryService.getGame(created.id(), white.id());

        assertAll(
                () -> assertEquals(
                        white.username(), reloaded.whiteUsernameAtStart()
                ),
                () -> assertEquals(
                        black.username(), reloaded.blackUsernameAtStart()
                ),
                () -> assertEquals(
                        created.whiteRatingAtStart(),
                        reloaded.whiteRatingAtStart()
                ),
                () -> assertEquals(
                        created.blackRatingAtStart(),
                        reloaded.blackRatingAtStart()
                ),
                () -> assertNotEquals(
                        whiteStartingRating,
                        playerStatisticsRepository.findById(white.id())
                                .orElseThrow().getRating()
                ),
                () -> assertNotEquals(
                        blackStartingRating,
                        playerStatisticsRepository.findById(black.id())
                                .orElseThrow().getRating()
                )
        );
    }

    @Test
    void gameWithoutParticipantSnapshotsDoesNotInventHistoricalDetails() {
        var legacy = new Game(
                white.id(),
                black.id(),
                EXPECTED_INITIAL_FEN
        );

        entityManager.persist(legacy);
        entityManager.flush();
        Long gameId = legacy.getId();
        entityManager.clear();

        var response = gameQueryService.getGame(gameId, white.id());

        assertAll(
                () -> assertNull(response.whiteUsernameAtStart()),
                () -> assertNull(response.blackUsernameAtStart()),
                () -> assertNull(response.whiteRatingAtStart()),
                () -> assertNull(response.blackRatingAtStart())
        );
    }

    private PlayerResponse createPlayer() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16);

        return playerService.createPlayer(
                "game_" + suffix,
                suffix + "@example.com"
        );
    }
}