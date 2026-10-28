package com.chessapp.game;

import com.chessapp.game.dto.GameResponse;
import com.chessapp.game.entity.Game;
import com.chessapp.game.service.GameService;
import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.service.PlayerService;
import com.chessapp.security.service.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.Square;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * @author pari on 10/23/26
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GameReadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private GameService gameService;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private JwtTokenService tokenService;

    private PlayerResponse white;
    private PlayerResponse black;
    private PlayerResponse outsider;
    private GameResponse game;
    private static final String EXPECTED_INITIAL_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                    + " w KQkq - 0 1";

    @BeforeEach
    void setUp() {
        white = createPlayer();
        black = createPlayer();
        outsider = createPlayer();

        game = gameService.createGame(white.id(), black.id());
    }

    @Test
    void getGame_whiteParticipantCanReadState() throws Exception {
        mockMvc.perform(get("/api/v1/games/{gameId}", game.id())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(white.id())
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(game.id()))
                .andExpect(jsonPath("$.whitePlayerId").value(white.id()))
                .andExpect(jsonPath("$.blackPlayerId").value(black.id()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.currentFen").value(EXPECTED_INITIAL_FEN))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.result").value(nullValue()))
                .andExpect(jsonPath("$.terminationReason").value(nullValue()))
                .andExpect(jsonPath("$.finishedAt").value(nullValue()));;
    }

    @Test
    void getGame_blackParticipantCanReadState() throws Exception {
        mockMvc.perform(get("/api/v1/games/{gameId}", game.id())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(black.id())
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(game.id()));
    }

    @Test
    void getGame_nonParticipantIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/games/{gameId}", game.id())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(outsider.id())
                        ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("GAME_ACCESS_DENIED"))
                .andExpect(jsonPath("$.currentFen").doesNotExist());
    }

    @Test
    void getGame_missingGameReturnsNotFound() throws Exception {
        // Generated game IDs are positive, making -1 a missing fixture
        // without relying on the database's current sequence value.
        mockMvc.perform(get("/api/v1/games/{gameId}", -1L)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(white.id())
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));
    }

    @Test
    void getGame_missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/games/{gameId}", game.id()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getGame_returnsAdvancedPositionAndVersion() throws Exception {
        var moved = gameService.submitMove(
                game.id(),
                white.id(),
                game.version(),
                new Move(
                        Square.fromAlgebraic("e2"),
                        Square.fromAlgebraic("e4")
                )
        );

        // Force GET to load persisted state rather than reuse the managed entity.
        // This remains inside the existing rollback-only test transaction.
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/games/{gameId}", game.id())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(black.id())
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(game.id()))
                .andExpect(jsonPath("$.currentFen").value(
                        "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
                                + " b KQkq e3 0 1"
                ))
                .andExpect(jsonPath("$.version").value(moved.version()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.result").value(nullValue()))
                .andExpect(jsonPath("$.terminationReason").value(nullValue()))
                .andExpect(jsonPath("$.finishedAt").value(nullValue()));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Qg6-g7 checkmates Black.
            "7k/8/5KQ1/8/8/8/8/8 w - - 4 20"
                    + " | g6 | g7"
                    + " | 7k/6Q1/5K2/8/8/8/8/8 b - - 5 20"
                    + " | WHITE_WIN | CHECKMATE",

            // Qf5-f7 leaves Black without a legal move and without check.
            "7k/8/6K1/5Q2/8/8/8/8 w - - 4 20"
                    + " | f5 | f7"
                    + " | 7k/5Q2/6K1/8/8/8/8/8 b - - 5 20"
                    + " | DRAW | STALEMATE"
    })
    void getGame_returnsCompletionDetails(
            String startingFen,
            String from,
            String to,
            String expectedFen,
            String expectedResult,
            String expectedReason
    ) throws Exception {
        // Seed a controlled pre-terminal fixture. The actual finishing move
        // still goes through the production gameplay service.
        var stored = entityManager.find(Game.class, game.id());
        stored.updatePosition(startingFen);

        entityManager.flush();
        entityManager.clear();

        // JPA fixture mutation increments the version, so observe it rather
        // than assuming this seeded game still has version zero.
        var beforeMove = gameService.getGame(game.id(), white.id());

        var completed = gameService.submitMove(
                game.id(),
                white.id(),
                beforeMove.version(),
                new Move(
                        Square.fromAlgebraic(from),
                        Square.fromAlgebraic(to)
                )
        );

        entityManager.flush();
        entityManager.clear();

        var reloaded = gameService.getGame(game.id(), black.id());

        assertEquals(beforeMove.version() + 1, completed.version().longValue());
        assertEquals(completed.finishedAt(), reloaded.finishedAt());

        mockMvc.perform(get("/api/v1/games/{gameId}", game.id())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(black.id())
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(game.id()))
                .andExpect(jsonPath("$.whitePlayerId").value(white.id()))
                .andExpect(jsonPath("$.blackPlayerId").value(black.id()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.currentFen").value(expectedFen))
                .andExpect(jsonPath("$.version").value(completed.version()))
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.result").value(expectedResult))
                .andExpect(jsonPath("$.terminationReason").value(expectedReason))
                .andExpect(jsonPath("$.finishedAt").isNotEmpty());
    }

    private String bearerToken(Long playerId) {
        // Exercise the configured JWT filter using a real signed token.
        return "Bearer " + tokenService.issueAccessToken(playerId)
                .getTokenValue();
    }

    private PlayerResponse createPlayer() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16);

        return playerService.createPlayer(
                "read_" + suffix,
                suffix + "@example.com"
        );
    }
}