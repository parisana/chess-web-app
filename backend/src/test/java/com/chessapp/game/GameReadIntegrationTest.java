package com.chessapp.game;

import com.chessapp.game.dto.GameResponse;
import com.chessapp.game.entity.Game;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.PieceType;
import com.chessapp.game.rules.model.Square;
import com.chessapp.game.service.GameQueryService;
import com.chessapp.game.service.GameCommandService;
import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.service.PlayerService;
import com.chessapp.support.TestSessions;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Autowired private MockMvc mockMvc;

    @Autowired private EntityManager entityManager;

    @Autowired private GameCommandService gameCommandService;

    @Autowired private GameQueryService gameQueryService;

    @Autowired private PlayerService playerService;

    @Autowired private SessionRepository<? extends Session> sessionRepository;

    @Autowired private CookieSerializer cookieSerializer;

    private TestSessions sessions;

    private PlayerResponse white;
    private PlayerResponse black;
    private PlayerResponse outsider;
    private GameResponse game;
    private static final String EXPECTED_INITIAL_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                    + " w KQkq - 0 1";

    @BeforeEach
    void setUp() {
        sessions = new TestSessions(sessionRepository, cookieSerializer);
        white = createPlayer();
        black = createPlayer();
        outsider = createPlayer();

        game = gameCommandService.createGame(white.id(), black.id());
    }

    @AfterEach
    void cleanUpSessions() {
        if (sessions != null) {
            sessions.close();
        }
    }

    @Test
    void getGame_whiteParticipantCanReadState() throws Exception {
        mockMvc.perform(get("/api/v1/games/{gameId}", game.id())
                        .cookie(sessions.forPlayer(white.id())))
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
                        .cookie(sessions.forPlayer(black.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(game.id()));
    }

    @Test
    void getGame_nonParticipantIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/games/{gameId}", game.id())
                        .cookie(sessions.forPlayer(outsider.id())))
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
                        .cookie(sessions.forPlayer(white.id())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));
    }

    @Test
    void getGame_missingSessionIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/games/{gameId}", game.id()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getGame_returnsAdvancedPositionAndVersion() throws Exception {
        var moved = gameCommandService.submitMove(
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
                        .cookie(sessions.forPlayer(black.id())))
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
        var beforeMove = gameQueryService.getGame(game.id(), white.id());

        var completed = gameCommandService.submitMove(
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

        var reloaded = gameQueryService.getGame(game.id(), black.id());

        assertEquals(beforeMove.version() + 1, completed.version().longValue());
        assertEquals(completed.finishedAt(), reloaded.finishedAt());

        mockMvc.perform(get("/api/v1/games/{gameId}", game.id())
                        .cookie(sessions.forPlayer(black.id())))
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

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void pgnBothParticipantsCanDownload(boolean asWhite) throws Exception {
        Long playerId = asWhite ? white.id() : black.id();

        mockMvc.perform(get("/api/v1/games/{gameId}/pgn", game.id())
                        .cookie(sessions.forPlayer(playerId)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        "application/x-chess-pgn"
                ))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"game-" + game.id() + ".pgn\""
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("\n\n*\n\n")
                ));
    }

    @Test
    void pgnRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/games/{gameId}/pgn", game.id()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pgnMissingGameReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/games/{gameId}/pgn", -1L)
                        .cookie(sessions.forPlayer(white.id())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));
    }

    @Test
    void pgnChecksMembershipBeforeLegacyAvailability() throws Exception {
        entityManager.flush();

        // Simulate migration-preserved history without changing applied migrations.
        entityManager.createNativeQuery("""
                    update games
                    set history_complete = false
                    where id = :id
                    """)
                .setParameter("id", game.id())
                .executeUpdate();

        entityManager.clear();

        mockMvc.perform(get("/api/v1/games/{gameId}/pgn", game.id())
                        .cookie(sessions.forPlayer(outsider.id())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GAME_ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/games/{gameId}/pgn", game.id())
                        .cookie(sessions.forPlayer(white.id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PGN_EXPORT_UNAVAILABLE"));
    }

    @Test
    void pgnUsesPersistedMovesAndLifecycleResult() throws Exception {
        var moved = gameCommandService.submitMove(
                game.id(),
                white.id(),
                game.version(),
                new Move(
                        Square.fromAlgebraic("e2"),
                        Square.fromAlgebraic("e4")
                )
        );

        // A lifecycle command advances the version without adding a SAN move.
        gameCommandService.resign(game.id(), black.id(), moved.version());

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/games/{gameId}/pgn", game.id())
                        .cookie(sessions.forPlayer(white.id())))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "[Result \"1-0\"]"
                        )
                ))
                .andExpect(content().string(
                        org.hamcrest.Matchers.endsWith(
                                "1. e4 1-0\n\n"
                        )
                ));
    }

    @Test
    void pgnRejectsPersistedPreSanMove() throws Exception {
        gameCommandService.submitMove(
                game.id(),
                white.id(),
                game.version(),
                new Move(
                        Square.fromAlgebraic("e2"),
                        Square.fromAlgebraic("e4")
                )
        );

        entityManager.flush();

        // V8 deliberately permits null SAN for older persisted records.
        entityManager.createNativeQuery("""
                    update game_moves
                    set san = null
                    where game_id = :id
                    """)
                .setParameter("id", game.id())
                .executeUpdate();

        entityManager.clear();

        mockMvc.perform(get("/api/v1/games/{gameId}/pgn", game.id())
                        .cookie(sessions.forPlayer(white.id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PGN_EXPORT_UNAVAILABLE"));
    }

    @Test
    void legalMovesReturnsVersionedInitialHintsWithoutChangingGame()
            throws Exception {
        mockMvc.perform(get(
                        "/api/v1/games/{gameId}/legal-moves",
                        game.id()
                ).cookie(sessions.forPlayer(white.id())))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(jsonPath("$.gameId").value(game.id()))
                .andExpect(jsonPath("$.version").value(game.version()))
                .andExpect(jsonPath("$.moves.length()").value(20));

        var hints = gameQueryService.getLegalMoves(game.id(), white.id());

        assertTrue(hints.moves().stream().anyMatch(move ->
                move.from().equals("e2")
                        && move.to().equals("e4")
                        && move.promotion() == null
                        && !move.capture()
        ));

        var after = gameQueryService.getGame(game.id(), white.id());

        assertEquals(game.version(), after.version());
        assertEquals(game.currentFen(), after.currentFen());
        assertEquals(0, gameQueryService.getHistory(game.id(), white.id())
                .recordedMoveCount());
    }

    @Test
    void otherParticipantCanReadTheSameSideToMoveHints()
            throws Exception {
        mockMvc.perform(get(
                        "/api/v1/games/{gameId}/legal-moves",
                        game.id()
                ).cookie(sessions.forPlayer(black.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moves.length()").value(20));
    }

    @Test
    void legalMovesRejectsOutsider() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/games/{gameId}/legal-moves",
                        game.id()
                ).cookie(sessions.forPlayer(outsider.id())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GAME_ACCESS_DENIED"))
                .andExpect(jsonPath("$.moves").doesNotExist());
    }

    @Test
    void legalMovesRequiresSession() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/games/{gameId}/legal-moves",
                        game.id()
                ))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legalMovesMissingGameReturnsNotFound() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/games/{gameId}/legal-moves",
                        -1L
                ).cookie(sessions.forPlayer(white.id())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));
    }

    @Test
    void finishedGameReturnsNoLegalMoveHints() throws Exception {
        var finished = gameCommandService.resign(
                game.id(),
                white.id(),
                game.version()
        );

        mockMvc.perform(get(
                        "/api/v1/games/{gameId}/legal-moves",
                        game.id()
                ).cookie(sessions.forPlayer(white.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(finished.version()))
                .andExpect(jsonPath("$.moves").isEmpty());
    }

    @Test
    void legalMoveHintsExcludeMovesThatExposeTheKing() {
        setHintPosition(
                "4r2k/8/8/8/8/8/4R3/4K3 w - - 0 1"
        );

        var hints = gameQueryService.getLegalMoves(game.id(), white.id());

        assertTrue(hints.moves().stream().noneMatch(move ->
                move.from().equals("e2") && move.to().equals("d2")
        ));

        // The pinned rook can still move along the pin line.
        assertTrue(hints.moves().stream().anyMatch(move ->
                move.from().equals("e2") && move.to().equals("e3")
        ));
    }

    @Test
    void enPassantHintIsMarkedAsCaptureDespiteEmptyDestination() {
        setHintPosition(
                "7k/8/8/3pP3/8/8/8/K7 w - d6 0 2"
        );

        var hints = gameQueryService.getLegalMoves(game.id(), white.id());

        assertTrue(hints.moves().stream().anyMatch(move ->
                move.from().equals("e5")
                        && move.to().equals("d6")
                        && move.capture()
        ));
    }

    @Test
    void promotionHintsRetainAllFourChoices() {
        setHintPosition(
                "7k/P7/8/8/8/8/8/K7 w - - 0 1"
        );

        var hints = gameQueryService.getLegalMoves(game.id(), white.id());

        var choices = hints.moves().stream()
                .filter(move ->
                        move.from().equals("a7") && move.to().equals("a8")
                )
                .map(move -> move.promotion())
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(
                        PieceType.QUEEN,
                        PieceType.ROOK,
                        PieceType.BISHOP,
                        PieceType.KNIGHT
                ),
                choices
        );
    }

    /**
     * Controlled position fixture for the read-only hints endpoint.
     * Does not represent a game played through the move/history pipeline.
     */
    private void setHintPosition(String fen) {
        entityManager.find(Game.class, game.id()).updatePosition(fen);
        entityManager.flush();
        entityManager.clear();
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