package com.chessapp.game;

import com.chessapp.game.dto.GameResponse;
import com.chessapp.game.entity.GameStatus;
import com.chessapp.game.repository.GameRepository;
import com.chessapp.game.service.GameService;
import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.service.PlayerService;
import com.chessapp.security.service.JwtTokenService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GameMoveHttpIntegrationTest {

    private static final String INITIAL =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                    + " w KQkq - 0 1";

    private static final String AFTER_E4 =
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
                    + " b KQkq e3 0 1";

    private static final String AFTER_E4_E5 =
            "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR"
                    + " w KQkq e6 0 2";

    @Autowired private MockMvc mockMvc;
    @Autowired private GameService gameService;
    @Autowired private GameRepository repository;
    @Autowired private PlayerService playerService;
    @Autowired private JwtTokenService tokenService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private List<PlayerResponse> players;
    private GameResponse game;

    @BeforeEach
    void setUp() {
        players = new TransactionTemplate(transactionManager)
                .execute(status -> List.of(
                        createPlayer(),
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

        Object[] ids = players.stream()
                .map(PlayerResponse::id)
                .toArray();

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    jdbc.update("""
                            DELETE FROM matchmaking_queue
                            WHERE player_id IN (?, ?, ?)
                            """, ids);

                    jdbc.update("""
                            DELETE FROM active_game_players
                            WHERE player_id IN (?, ?, ?)
                            """, ids);

                    jdbc.update("""
                            DELETE FROM game_moves
                            WHERE game_id IN (
                                SELECT id
                                FROM games
                                WHERE white_player_id IN (?, ?, ?)
                            )
                            """, ids);
                    // Every game created here belongs to fixture players.
                    jdbc.update("""
                            DELETE FROM games
                            WHERE white_player_id IN (?, ?, ?)
                            """, ids);

                    jdbc.update("""
                            DELETE FROM players
                            WHERE id IN (?, ?, ?)
                            """, ids);
                });
    }

    @Test
    void bothPlayersCanSubmitMovesAndReadCommittedState() throws Exception {
        String first = submit(white(), 0, "e2", "e4", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionStatus").value("NORMAL"))
                .andExpect(jsonPath("$.result").value(nullValue()))
                .andExpect(jsonPath("$.terminationReason").value(nullValue()))
                .andExpect(jsonPath("$.finishedAt").value(nullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertCommittedState(
                first, AFTER_E4, 1, GameStatus.IN_PROGRESS, 2
        );

        String second = submit(black(), 1, "e7", "e5", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionStatus").value("NORMAL"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertCommittedState(
                second, AFTER_E4_E5, 2, GameStatus.IN_PROGRESS, 2
        );
    }

    @Test
    void submissionRequiresAuthentication() throws Exception {
        String body = moveBody(0, "e2", "e4", null);

        mockMvc.perform(post("/api/v1/games/{gameId}/moves", game.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/games/{gameId}/moves", game.id())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer definitely-not-a-jwt"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        assertUnchanged(INITIAL, 0, 2);
    }

    @Test
    void wellFormedRejectionsPreserveServiceFailurePrecedence()
            throws Exception {

        // Lookup precedes participant membership.
        mockMvc.perform(post("/api/v1/games/{gameId}/moves", -1L)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(outsider())
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(moveBody(99, "e2", "e5", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));

        // Access precedes version, turn and legality.
        submit(outsider(), 99, "e2", "e5", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GAME_ACCESS_DENIED"))
                .andExpect(jsonPath("$.currentFen").doesNotExist());

        // Version precedes turn.
        submit(black(), 99, "e7", "e5", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_GAME_VERSION"));

        // The JWT identifies Black, even though the coordinates name
        // a White piece. Coordinates cannot choose the acting player.
        submit(black(), 0, "e2", "e4", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_PLAYERS_TURN"));

        submit(white(), 0, "e2", "e5", null)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("ILLEGAL_GAME_MOVE"));

        assertUnchanged(INITIAL, 0, 2);
    }

    @Test
    void staleCommandDoesNotOverwriteAnAcceptedMove() throws Exception {
        submit(white(), 0, "e2", "e4", null)
                .andExpect(status().isOk());

        // Black owns the current turn, but has an obsolete version.
        submit(black(), 0, "e7", "e5", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_GAME_VERSION"));

        assertUnchanged(AFTER_E4, 1, 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"from\":\"e2\",\"to\":\"e4\"}",
            "{\"expectedVersion\":null,\"from\":\"e2\",\"to\":\"e4\"}",
            "{\"expectedVersion\":-1,\"from\":\"e2\",\"to\":\"e4\"}",
            "{\"expectedVersion\":0,\"to\":\"e4\"}",
            "{\"expectedVersion\":0,\"from\":\"e2\"}",
            "{\"expectedVersion\":0,\"from\":\"\",\"to\":\"e4\"}",
            "{\"expectedVersion\":0,\"from\":\"E2\",\"to\":\"e4\"}",
            "{\"expectedVersion\":0,\"from\":\"e2\",\"to\":\"i4\"}",
            "{\"expectedVersion\":0,\"from\":\"e2\",\"to\":\"e2\"}",
            "{\"expectedVersion\":0,\"from\":\"e7\",\"to\":\"e8\","
                    + "\"promotion\":\"KING\"}",
            "{\"expectedVersion\":0,\"from\":\"e7\",\"to\":\"e8\","
                    + "\"promotion\":\"queen\"}"
    })
    void invalidFieldsReturnValidationDetailsWithoutMutation(String body)
            throws Exception {

        mockMvc.perform(post("/api/v1/games/{gameId}/moves", game.id())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(white())
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors").isNotEmpty());

        assertUnchanged(INITIAL, 0, 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"expectedVersion\":",
            "{\"expectedVersion\":{},\"from\":\"e2\",\"to\":\"e4\"}"
    })
    void malformedJsonOrIncompatibleTypesReturnBadRequest(String body)
            throws Exception {

        mockMvc.perform(post("/api/v1/games/{gameId}/moves", game.id())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(white())
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        // These framework failures do not promise VALIDATION_FAILED.
        assertUnchanged(INITIAL, 0, 2);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Castling for both colors.
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 4 20"
                    + " | e1 | g1 | -"
                    + " | r3k2r/8/8/8/8/8/8/R4RK1 b kq - 5 20",
            "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 4 20"
                    + " | e8 | c8 | -"
                    + " | 2kr3r/8/8/8/8/8/8/R3K2R w KQ - 5 21",

            // En passant for both colors.
            "7k/8/8/3pP3/8/8/8/K7 w - d6 0 20"
                    + " | e5 | d6 | -"
                    + " | 7k/8/3P4/8/8/8/8/K7 b - - 0 20",
            "k7/8/8/8/3Pp3/8/8/7K b - d3 0 20"
                    + " | e4 | d3 | -"
                    + " | k7/8/8/8/8/3p4/8/7K w - - 0 21",

            // Explicit capture and quiet underpromotions.
            "3r3k/4P3/8/8/8/8/8/K7 w - - 0 20"
                    + " | e7 | d8 | KNIGHT"
                    + " | 3N3k/8/8/8/8/8/8/K7 b - - 0 20",
            "k7/8/8/8/8/8/4p3/7K b - - 0 20"
                    + " | e2 | e1 | ROOK"
                    + " | k7/8/8/8/8/8/8/4r2K w - - 0 21"
    })
    void specialMovesPassThroughTheHttpBoundary(
            String startingFen,
            String from,
            String to,
            String promotion,
            String expectedFen
    ) throws Exception {
        seedFen(startingFen);

        String body = submit(
                mover(startingFen),
                0,
                from,
                to,
                promotion.equals("-") ? null : promotion
        )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertCommittedState(
                body, expectedFen, 1, GameStatus.IN_PROGRESS, 2
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Promotion is required here; there is no queen default.
            "7k/4P3/8/8/8/8/8/K7 w - - 0 20"
                    + " | e7 | e8 | -",

            // A supported promotion name is still illegal on an ordinary move.
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                    + " w KQkq - 0 1"
                    + " | e2 | e4 | QUEEN"
    })
    void boardDependentPromotionFailuresReturnIllegalMove(
            String startingFen,
            String from,
            String to,
            String promotion
    ) throws Exception {
        seedFen(startingFen);

        submit(
                white(),
                0,
                from,
                to,
                promotion.equals("-") ? null : promotion
        )
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("ILLEGAL_GAME_MOVE"));

        assertUnchanged(startingFen, 0, 2);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // White mates Black.
            "7k/8/5KQ1/8/8/8/8/8 w - - 4 20"
                    + " | g6 | g7"
                    + " | 7k/6Q1/5K2/8/8/8/8/8 b - - 5 20"
                    + " | WHITE_WIN | CHECKMATE",

            // Black mates White.
            "8/8/8/8/8/5kq1/8/7K b - - 4 20"
                    + " | g3 | g2"
                    + " | 8/8/8/8/8/5k2/6q1/7K w - - 5 21"
                    + " | BLACK_WIN | CHECKMATE",

            // White stalemates Black.
            "7k/8/6K1/5Q2/8/8/8/8 w - - 4 20"
                    + " | f5 | f7"
                    + " | 7k/5Q2/6K1/8/8/8/8/8 b - - 5 20"
                    + " | DRAW | STALEMATE",

            // Black stalemates White.
            "8/8/8/8/5q2/6k1/8/7K b - - 4 20"
                    + " | f4 | f2"
                    + " | 8/8/8/8/8/6k1/5q2/7K w - - 5 21"
                    + " | DRAW | STALEMATE"
    })
    void terminalMovesCommitResultsAndReleaseReservations(
            String startingFen,
            String from,
            String to,
            String expectedFen,
            String expectedResult,
            String expectedReason
    ) throws Exception {
        seedFen(startingFen);
        Long actor = mover(startingFen);

        String body = submit(actor, 0, from, to, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionStatus").value(expectedReason))
                .andExpect(jsonPath("$.result").value(expectedResult))
                .andExpect(jsonPath("$.terminationReason").value(expectedReason))
                .andExpect(jsonPath("$.finishedAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertCommittedState(
                body, expectedFen, 1, GameStatus.FINISHED, 0
        );

        // Finished lifecycle precedes the now-stale expected version.
        submit(actor, 0, from, to, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GAME_ALREADY_FINISHED"));

        // Participant access still precedes lifecycle.
        submit(outsider(), 0, from, to, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GAME_ACCESS_DENIED"));

        // Rejected follow-up commands preserve the entire committed result.
        assertCommittedState(
                body, expectedFen, 1, GameStatus.FINISHED, 0
        );
        for (Long participant : List.of(white(), black())) {
            readHistory(participant)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recordedMoveCount").value(1))
                    .andExpect(jsonPath("$.moves.length()").value(1))
                    .andExpect(jsonPath("$.moves[0].resultingFen").value(expectedFen));
        }
        assertTrue(gameService.findActiveGameId(white()).isEmpty());
        assertTrue(gameService.findActiveGameId(black()).isEmpty());
    }

    @Test
    void newGameHasAnEmptyHistoryFromItsInitialPosition() throws Exception {
        readHistory(white())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(game.id()))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.historyStartFen").value(INITIAL))
                .andExpect(jsonPath("$.historyComplete").value(true))
                .andExpect(jsonPath("$.recordedMoveCount").value(0))
                .andExpect(jsonPath("$.moves").isEmpty());
    }

    @Test
    void bothParticipantsCanReadOrderedMoveHistory() throws Exception {
        submit(white(), 0, "e2", "e4", null)
                .andExpect(status().isOk());

        submit(black(), 1, "e7", "e5", null)
                .andExpect(status().isOk());

        for (Long participant : List.of(white(), black())) {
            readHistory(participant)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(2))
                    .andExpect(jsonPath("$.historyStartFen").value(INITIAL))
                    .andExpect(jsonPath("$.historyComplete").value(true))
                    .andExpect(jsonPath("$.recordedMoveCount").value(2))
                    .andExpect(jsonPath("$.moves.length()").value(2))

                    .andExpect(jsonPath("$.moves[0].sequenceNumber").value(1))
                    .andExpect(jsonPath("$.moves[0].playerId").value(white()))
                    .andExpect(jsonPath("$.moves[0].from").value("e2"))
                    .andExpect(jsonPath("$.moves[0].to").value("e4"))
                    .andExpect(jsonPath("$.moves[0].promotion").value(nullValue()))
                    .andExpect(jsonPath("$.moves[0].resultingFen").value(AFTER_E4))
                    .andExpect(jsonPath("$.moves[0].playedAt").isNotEmpty())

                    .andExpect(jsonPath("$.moves[1].sequenceNumber").value(2))
                    .andExpect(jsonPath("$.moves[1].playerId").value(black()))
                    .andExpect(jsonPath("$.moves[1].from").value("e7"))
                    .andExpect(jsonPath("$.moves[1].to").value("e5"))
                    .andExpect(jsonPath("$.moves[1].resultingFen").value(AFTER_E4_E5))
                    .andExpect(jsonPath("$.moves[0].san").value("e4"))
                    .andExpect(jsonPath("$.moves[1].san").value("e5"));
        }
    }

    @Test
    void historyRequiresAuthentication() throws Exception {
        mockMvc.perform(
                        get("/api/v1/games/{gameId}/moves", game.id())
                )
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        get("/api/v1/games/{gameId}/moves", game.id())
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer definitely-not-a-jwt"
                                )
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void historyRejectsOutsidersAndReportsMissingGames() throws Exception {
        readHistory(outsider())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GAME_ACCESS_DENIED"));

        mockMvc.perform(
                        get("/api/v1/games/{gameId}/moves", -1L)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearerToken(white())
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));
    }

    @Test
    void legacyHistoryExposesItsBaselineAndIncompleteFlag() throws Exception {
        jdbc.update("""
            UPDATE games
            SET current_fen = ?,
                history_start_fen = ?,
                history_complete = FALSE
            WHERE id = ?
            """,
                AFTER_E4,
                AFTER_E4,
                game.id()
        );

        submit(black(), 0, "e7", "e5", null)
                .andExpect(status().isOk());

        readHistory(white())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyStartFen").value(AFTER_E4))
                .andExpect(jsonPath("$.historyComplete").value(false))
                .andExpect(jsonPath("$.recordedMoveCount").value(1))
                .andExpect(jsonPath("$.moves.length()").value(1))
                .andExpect(jsonPath("$.moves[0].sequenceNumber").value(1))
                .andExpect(jsonPath("$.moves[0].playerId").value(black()))
                .andExpect(jsonPath("$.moves[0].from").value("e7"))
                .andExpect(jsonPath("$.moves[0].to").value("e5"))
                .andExpect(jsonPath("$.moves[0].resultingFen").value(AFTER_E4_E5));
    }

    @Test
    void historyExposesPawnAndKnightCapturesAsSan() throws Exception {
        submit(white(), 0, "e2", "e4", null)
                .andExpect(status().isOk());

        submit(black(), 1, "d7", "d5", null)
                .andExpect(status().isOk());

        submit(white(), 2, "e4", "d5", null)
                .andExpect(status().isOk());

        submit(black(), 3, "g8", "f6", null)
                .andExpect(status().isOk());

        submit(white(), 4, "g1", "f3", null)
                .andExpect(status().isOk());

        submit(black(), 5, "f6", "d5", null)
                .andExpect(status().isOk());

        readHistory(white())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moves.length()").value(6))
                .andExpect(jsonPath("$.moves[0].san").value("e4"))
                .andExpect(jsonPath("$.moves[1].san").value("d5"))
                .andExpect(jsonPath("$.moves[2].san").value("exd5"))
                .andExpect(jsonPath("$.moves[3].san").value("Nf6"))
                .andExpect(jsonPath("$.moves[4].san").value("Nf3"))
                .andExpect(jsonPath("$.moves[5].san").value("Nxd5"));
    }

    @Test
    void historyRemainsReadableWhenAnOlderEntryHasNoSan() throws Exception {
        submit(white(), 0, "e2", "e4", null)
                .andExpect(status().isOk());

        // Simulate an entry recorded before migration V8.
        assertEquals(
                1,
                jdbc.update("""
                    UPDATE game_moves
                    SET san = NULL
                    WHERE game_id = ?
                      AND sequence_number = 1
                    """,
                        game.id()
                )
        );

        readHistory(white())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyComplete").value(true))
                .andExpect(jsonPath("$.moves[0].san").value(nullValue()))
                .andExpect(jsonPath("$.moves[0].from").value("e2"))
                .andExpect(jsonPath("$.moves[0].to").value("e4"));
    }

    @ParameterizedTest
    @CsvSource({
            "true, BLACK_WIN",
            "false, WHITE_WIN"
    })
    void resignationReturnsThePersistedOutcome(
            boolean whiteResigns,
            String expectedResult
    ) throws Exception {
        Long actor = whiteResigns ? white() : black();

        resign(actor, game.version())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(game.id()))
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.result").value(expectedResult))
                .andExpect(jsonPath("$.terminationReason").value("RESIGNATION"))
                .andExpect(jsonPath("$.finishedAt").isNotEmpty())
                .andExpect(jsonPath("$.version").value(game.version() + 1))
                .andExpect(jsonPath("$.currentFen").value(INITIAL));

        // Both participants can read the same committed outcome.
        for (Long participant : List.of(white(), black())) {
            mockMvc.perform(get("/api/v1/games/{gameId}", game.id())
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    bearerToken(participant)
                            ))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FINISHED"))
                    .andExpect(jsonPath("$.result").value(expectedResult))
                    .andExpect(jsonPath("$.terminationReason")
                            .value("RESIGNATION"));
        }

        readHistory(white())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(game.version() + 1))
                .andExpect(jsonPath("$.recordedMoveCount").value(0))
                .andExpect(jsonPath("$.moves").isEmpty());

        assertTrue(gameService.findActiveGameId(white()).isEmpty());
        assertTrue(gameService.findActiveGameId(black()).isEmpty());
    }

    @Test
    void resignationRequiresAuthentication() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/games/{gameId}/resignation",
                        game.id()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"expectedVersion": 0}
                            """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(
                        "/api/v1/games/{gameId}/resignation",
                        game.id()
                )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer definitely-not-a-jwt"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"expectedVersion": 0}
                            """))
                .andExpect(status().isUnauthorized());

        assertUnchanged(INITIAL, 0, 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"expectedVersion\": null}",
            "{\"expectedVersion\": -1}"
    })
    void invalidResignationVersionReturnsValidationFailure(String body)
            throws Exception {
        mockMvc.perform(post(
                        "/api/v1/games/{gameId}/resignation",
                        game.id()
                )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(white())
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertUnchanged(INITIAL, 0, 2);
    }

    @Test
    void resignationPreservesFailurePrecedence() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/games/{gameId}/resignation",
                        -1L
                )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(outsider())
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"expectedVersion": 99}
                            """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));

        resign(outsider(), 99)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GAME_ACCESS_DENIED"));

        resign(white(), 99)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_GAME_VERSION"));

        assertUnchanged(INITIAL, 0, 2);

        resign(white(), 0)
                .andExpect(status().isOk());

        // Membership still takes precedence after completion.
        resign(outsider(), 0)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GAME_ACCESS_DENIED"));

        // Lifecycle takes precedence over the now-stale version.
        resign(black(), 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GAME_ALREADY_FINISHED"));
    }

    @Test
    void resignationPreservesPreviouslyRecordedMoves() throws Exception {
        submit(white(), 0, "e2", "e4", null)
                .andExpect(status().isOk());

        resign(black(), 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("WHITE_WIN"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.currentFen").value(AFTER_E4));

        // Two state changes, but only one chess move.
        readHistory(white())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.recordedMoveCount").value(1))
                .andExpect(jsonPath("$.moves.length()").value(1))
                .andExpect(jsonPath("$.moves[0].san").value("e4"))
                .andExpect(jsonPath("$.moves[0].resultingFen").value(AFTER_E4));
    }

    @Test
    void drawWorkflowExposesPendingOffersAndAgreedCompletion()
            throws Exception {
        drawCommand(white(), "", 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.drawOfferedByPlayerId").value(white()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // The opponent discovers the offer through the regular snapshot GET.
        mockMvc.perform(get("/api/v1/games/{gameId}", game.id())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(black())
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.drawOfferedByPlayerId").value(white()));

        drawCommand(black(), "/decline", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.drawOfferedByPlayerId").value(nullValue()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        drawCommand(black(), "", 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3));

        drawCommand(black(), "/withdraw", 3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(4))
                .andExpect(jsonPath("$.drawOfferedByPlayerId").value(nullValue()));

        drawCommand(white(), "", 4)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(5));

        drawCommand(black(), "/accept", 5)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(6))
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.result").value("DRAW"))
                .andExpect(jsonPath("$.terminationReason").value("DRAW_AGREEMENT"))
                .andExpect(jsonPath("$.finishedAt").isNotEmpty())
                .andExpect(jsonPath("$.drawOfferedByPlayerId").value(nullValue()))
                .andExpect(jsonPath("$.currentFen").value(INITIAL));

        readHistory(black())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(6))
                .andExpect(jsonPath("$.recordedMoveCount").value(0))
                .andExpect(jsonPath("$.moves").isEmpty());

        assertTrue(gameService.findActiveGameId(white()).isEmpty());
        assertTrue(gameService.findActiveGameId(black()).isEmpty());

        // Completion blocks subsequent moves.
        submit(white(), 6, "e2", "e4", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GAME_ALREADY_FINISHED"));
    }

    @Test
    void drawOfferConflictsExposeTheirApplicationCodes() throws Exception {
        for (String action : List.of("/accept", "/decline", "/withdraw")) {
            drawCommand(black(), action, 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DRAW_OFFER_NOT_FOUND"));
        }

        drawCommand(white(), "", 0)
                .andExpect(status().isOk());

        drawCommand(black(), "", 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRAW_OFFER_ALREADY_PENDING"));

        drawCommand(white(), "/accept", 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRAW_OFFER_ACTION_NOT_ALLOWED"));

        drawCommand(white(), "/decline", 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRAW_OFFER_ACTION_NOT_ALLOWED"));

        drawCommand(black(), "/withdraw", 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRAW_OFFER_ACTION_NOT_ALLOWED"));
    }

    // action will be "", "/accept", "/decline" or "/withdraw"
    private ResultActions drawCommand(
            Long actor,
            String action,
            long expectedVersion
    ) throws Exception {
        return mockMvc.perform(
                post(
                        "/api/v1/games/{gameId}/draw-offer" + action,
                        game.id()
                )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(actor)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"expectedVersion": %d}
                            """.formatted(expectedVersion))
        );
    }

    private ResultActions submit(
            Long actor,
            long expectedVersion,
            String from,
            String to,
            String promotion
    ) throws Exception {
        return mockMvc.perform(
                post("/api/v1/games/{gameId}/moves", game.id())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(actor)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(moveBody(
                                expectedVersion, from, to, promotion
                        ))
        );
    }

    private ResultActions resign(
            Long actor,
            long expectedVersion
    ) throws Exception {
        return mockMvc.perform(
                post("/api/v1/games/{gameId}/resignation", game.id())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(actor)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"expectedVersion": %d}
                            """.formatted(expectedVersion))
        );
    }

    private static String moveBody(
            long expectedVersion,
            String from,
            String to,
            String promotion
    ) {
        // Only controlled test fixture values enter this JSON helper.
        String promotionJson =
                promotion == null ? "null" : "\"" + promotion + "\"";

        return """
                {
                  "expectedVersion": %d,
                  "from": "%s",
                  "to": "%s",
                  "promotion": %s
                }
                """.formatted(expectedVersion, from, to, promotionJson);
    }

    private void assertCommittedState(
            String postBody,
            String expectedFen,
            long expectedVersion,
            GameStatus expectedStatus,
            int expectedReservations
    ) throws Exception {
        // No enclosing test transaction: repository reads get a fresh context.
        var stored = repository.findById(game.id()).orElseThrow();
        Map<String, Object> posted = JsonPath.read(postBody, "$");

        assertEquals(expectedFen, stored.getCurrentFen());
        assertEquals(expectedVersion, stored.getVersion().longValue());
        assertEquals(expectedStatus, stored.getStatus());
        assertEquals(expectedReservations, reservationCount());

        assertEquals(game.id().longValue(), number(posted, "gameId"));
        assertEquals(stored.getCurrentFen(), posted.get("currentFen"));
        assertEquals(stored.getVersion().longValue(), number(posted, "version"));
        assertEquals(stored.getStatus().name(), posted.get("status"));

        assertEquals(
                stored.getResult() == null ? null : stored.getResult().name(),
                posted.get("result")
        );
        assertEquals(
                stored.getTerminationReason() == null
                        ? null
                        : stored.getTerminationReason().name(),
                posted.get("terminationReason")
        );

        Object timestamp = posted.get("finishedAt");
        assertEquals(
                stored.getFinishedAt(),
                timestamp == null ? null : Instant.parse((String) timestamp)
        );

        // Both participants can reload the same state through HTTP.
        for (Long participant : List.of(white(), black())) {
            String getBody = mockMvc.perform(
                            get("/api/v1/games/{gameId}", game.id())
                                    .header(
                                            HttpHeaders.AUTHORIZATION,
                                            bearerToken(participant)
                                    ))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            Map<String, Object> read = JsonPath.read(getBody, "$");

            assertEquals(game.id().longValue(), number(read, "id"));

            for (String field : List.of(
                    "currentFen",
                    "version",
                    "status",
                    "result",
                    "terminationReason",
                    "finishedAt"
            )) {
                // Require completion keys even when their values are null.
                assertTrue(posted.containsKey(field), field);
                assertTrue(read.containsKey(field), field);
                assertEquals(posted.get(field), read.get(field), field);
            }
        }
    }

    private void assertUnchanged(
            String expectedFen,
            long expectedVersion,
            int expectedReservations
    ) {
        var stored = repository.findById(game.id()).orElseThrow();

        assertAll(
                () -> assertEquals(expectedFen, stored.getCurrentFen()),
                () -> assertEquals(
                        expectedVersion, stored.getVersion().longValue()
                ),
                () -> assertEquals(GameStatus.IN_PROGRESS, stored.getStatus()),
                () -> assertNull(stored.getResult()),
                () -> assertNull(stored.getTerminationReason()),
                () -> assertNull(stored.getFinishedAt()),
                () -> assertEquals(
                        expectedReservations, reservationCount()
                )
        );
    }

    private void seedFen(String fen) {
        // Controlled fixture setup before any moves.
        // Treat this custom position as the fixture game's starting position.
        // Direct SQL intentionally leaves its JPA version at zero.
        assertEquals(
                1,
                jdbc.update("""
                    UPDATE games
                    SET current_fen = ?,
                        history_start_fen = ?
                    WHERE id = ?
                    """,
                        fen,
                        fen,
                        game.id()
                )
        );
    }

    private int reservationCount() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM active_game_players WHERE game_id = ?",
                Integer.class,
                game.id()
        );

        assertNotNull(count);
        return count;
    }

    private static long number(Map<String, Object> fields, String name) {
        return ((Number) fields.get(name)).longValue();
    }

    private String bearerToken(Long playerId) {
        // Exercise the real security filter and configured JWT decoder.
        return "Bearer " + tokenService.issueAccessToken(playerId)
                .getTokenValue();
    }

    private Long white() {
        return players.getFirst().id();
    }

    private Long black() {
        return players.get(1).id();
    }

    private Long outsider() {
        return players.get(2).id();
    }

    private Long mover(String fen) {
        return fen.split(" ")[1].equals("w") ? white() : black();
    }

    private PlayerResponse createPlayer() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16);

        return playerService.createPlayer(
                "http_" + suffix,
                suffix + "@example.com"
        );
    }

    private ResultActions readHistory(Long playerId) throws Exception {
        return mockMvc.perform(
                get("/api/v1/games/{gameId}/moves", game.id())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(playerId)
                        )
        );
    }
}