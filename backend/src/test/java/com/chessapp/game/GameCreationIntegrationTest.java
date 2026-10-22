package com.chessapp.game;

import com.chessapp.game.entity.Game;
import com.chessapp.game.entity.GameStatus;
import com.chessapp.game.repository.GameRepository;
import com.chessapp.game.service.GameService;
import com.chessapp.player.dto.PlayerResponse;
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
    private GameService gameService;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private EntityManager entityManager;

    private PlayerResponse white;
    private PlayerResponse black;

    @BeforeEach
    void setUp() {
        white = createPlayer();
        black = createPlayer();
    }

    @Test
    void createGame_persistsInitialState() {
        var response = gameService.createGame(
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
                        Game.INITIAL_FEN, response.currentFen()
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
                () -> gameService.createGame(
                        white.id(),
                        white.id()
                )
        );
    }

    @Test
    void createGame_rejectsPlayerThatNoLongerExists() {
        Long deletedPlayerId = black.id();

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
                () -> gameService.createGame(
                        white.id(),
                        deletedPlayerId
                )
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