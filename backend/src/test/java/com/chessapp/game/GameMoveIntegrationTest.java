package com.chessapp.game;

import com.chessapp.BoardTestRenderer;
import com.chessapp.game.dto.GameMoveResult;
import com.chessapp.game.dto.GameResponse;
import com.chessapp.game.entity.*;
import com.chessapp.game.event.GameChangedEvent;
import com.chessapp.game.exception.*;
import com.chessapp.game.repository.GameMoveRepository;
import com.chessapp.game.repository.GameRepository;
import com.chessapp.game.rules.evaluation.PositionStatus;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.PieceType;
import com.chessapp.game.rules.model.Square;
import com.chessapp.game.service.GameService;
import com.chessapp.matchmaking.dto.QueueStatus;
import com.chessapp.matchmaking.service.MatchmakingService;
import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.service.PlayerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.chessapp.notification.config.WebSocketProperties;
import com.chessapp.notification.dto.PlayerUpdate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

//This class deliberately has no class-level @Transactional. Successful service calls must actually commit, and subsequent reads must observe durable state.
@SpringBootTest
@ActiveProfiles("test")
class GameMoveIntegrationTest {

    private static final String INITIAL =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                    + " w KQkq - 0 1";

    private static final String BEFORE_WHITE_MATE =
            "7k/8/5KQ1/8/8/8/8/8 w - - 4 20";

    @Autowired private GameService service;
    @Autowired private GameRepository repository;
    @Autowired private GameMoveRepository moveRepository;
    @Autowired private PlayerService playerService;
    @Autowired private MatchmakingService matchmaking;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean
    private SimpMessagingTemplate outboundMessages;

    @Autowired
    private WebSocketProperties webSocketProperties;

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
        game = service.createGame(white(), black());
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
                    // All games created here use only these fixture players.
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
    void ordinaryMovesPersistVersionsAndRetainReservations() {
        var first = submit(white(), 0, "e2", "e4");

        assertEquals(
                "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
                        + " b KQkq e3 0 1",
                first.currentFen()
        );
        assertEquals(Long.valueOf(1), first.version());
        assertEquals(PositionStatus.NORMAL, first.positionStatus());
        assertEquals(GameStatus.IN_PROGRESS, first.status());
        assertNull(first.result());
        assertNull(first.terminationReason());
        assertNull(first.finishedAt());

        var second = submit(black(), 1, "e7", "e5");

        assertEquals(
                "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR"
                        + " w KQkq e6 0 2",
                second.currentFen()
        );

        var persisted = reload();

        assertEquals(second.currentFen(), persisted.getCurrentFen());
        assertEquals(Long.valueOf(2), persisted.getVersion());
        assertEquals(
                second.currentFen(),
                service.getGame(game.id(), black()).currentFen()
        );
        assertReservations();

        var moves = history();

        assertEquals(2, moves.size());

        var whiteMove = moves.getFirst();
        var blackMove = moves.get(1);

        assertEquals("e4", whiteMove.getSan());
        assertEquals("e5", blackMove.getSan());

        assertAll(
                () -> assertEquals(1L, whiteMove.getSequenceNumber()),
                () -> assertEquals(white(), whiteMove.getPlayerId()),
                () -> assertEquals("e2", whiteMove.getFromSquare()),
                () -> assertEquals("e4", whiteMove.getToSquare()),
                () -> assertNull(whiteMove.getPromotion()),
                () -> assertEquals(
                        first.currentFen(),
                        whiteMove.getResultingFen()
                ),
                () -> assertNotNull(whiteMove.getPlayedAt()),

                () -> assertEquals(2L, blackMove.getSequenceNumber()),
                () -> assertEquals(black(), blackMove.getPlayerId()),
                () -> assertEquals("e7", blackMove.getFromSquare()),
                () -> assertEquals("e5", blackMove.getToSquare()),
                () -> assertEquals(
                        second.currentFen(),
                        blackMove.getResultingFen()
                ),

                () -> assertEquals(
                        2L,
                        persisted.getRecordedMoveCount()
                ),
                () -> assertEquals(
                        INITIAL,
                        persisted.getHistoryStartFen()
                ),
                () -> assertTrue(persisted.isHistoryComplete())
        );
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
    void specialMovesUseThePersistedPipeline(
            String fen,
            String from,
            String to,
            String promotion,
            String expectedFen
    ) {
        seedFen(fen);

        var result = service.submitMove(
                game.id(),
                mover(fen),
                0,
                move(from, to, promotion)
        );

        assertEquals(expectedFen, result.currentFen());
        assertEquals(expectedFen, reload().getCurrentFen());
        assertEquals(Long.valueOf(1), reload().getVersion());
        assertEquals(GameStatus.IN_PROGRESS, result.status());
        assertReservations();

        var moves = history();
        assertEquals(1, moves.size());

        var recorded = moves.getFirst();

        assertAll(
                () -> assertEquals(1L, recorded.getSequenceNumber()),
                () -> assertEquals(mover(fen), recorded.getPlayerId()),
                () -> assertEquals(from, recorded.getFromSquare()),
                () -> assertEquals(to, recorded.getToSquare()),
                () -> assertEquals(
                        promotion.equals("-")
                                ? null
                                : PieceType.valueOf(promotion),
                        recorded.getPromotion()
                ),
                () -> assertEquals(
                        expectedFen,
                        recorded.getResultingFen()
                ),
                () -> assertEquals(
                        1L,
                        reload().getRecordedMoveCount()
                )
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // White mates Black.
            "7k/8/5KQ1/8/8/8/8/8 w - - 4 20"
                    + " | g6 | g7"
                    + " | 7k/6Q1/5K2/8/8/8/8/8 b - - 5 20"
                    + " | CHECKMATE | WHITE_WIN",

            // Black mates White.
            "8/8/8/8/8/5kq1/8/7K b - - 4 20"
                    + " | g3 | g2"
                    + " | 8/8/8/8/8/5k2/6q1/7K w - - 5 21"
                    + " | CHECKMATE | BLACK_WIN",

            // White stalemates Black.
            "7k/8/6K1/5Q2/8/8/8/8 w - - 4 20"
                    + " | f5 | f7"
                    + " | 7k/5Q2/6K1/8/8/8/8/8 b - - 5 20"
                    + " | STALEMATE | DRAW",

            // Black stalemates White.
            "8/8/8/8/5q2/6k1/8/7K b - - 4 20"
                    + " | f4 | f2"
                    + " | 8/8/8/8/8/6k1/5q2/7K w - - 5 21"
                    + " | STALEMATE | DRAW"
    })
    void terminalMovesPersistCompletionAndReleaseBothPlayers(
            String fen,
            String from,
            String to,
            String expectedFen,
            PositionStatus assessment,
            GameResult expectedResult
    ) {
        seedFen(fen);
        var proposal = move(from, to);

        System.out.println(BoardTestRenderer.render(
                new FenParser().parse(fen),
                proposal.from(),
                Set.of(proposal)
        ));

        var response = service.submitMove(
                game.id(), mover(fen), 0, proposal
        );
        var persisted = reload();

        assertAll(
                () -> assertEquals(
                        expectedFen, persisted.getCurrentFen()
                ),
                () -> assertEquals(
                        Long.valueOf(1), persisted.getVersion()
                ),
                () -> assertEquals(
                        assessment, response.positionStatus()
                ),
                () -> assertEquals(
                        GameStatus.FINISHED, persisted.getStatus()
                ),
                () -> assertEquals(
                        expectedResult, persisted.getResult()
                ),
                () -> assertEquals(
                        GameTerminationReason.valueOf(assessment.name()),
                        persisted.getTerminationReason()
                ),
                () -> assertNotNull(persisted.getFinishedAt()),
                () -> assertEquals(
                        response.finishedAt(), persisted.getFinishedAt()
                ),
                () -> assertTrue(
                        service.findActiveGameId(white()).isEmpty()
                ),
                () -> assertTrue(
                        service.findActiveGameId(black()).isEmpty()
                ),
                () -> assertEquals(0, reservationCount())
        );

        assertEquals(
                GameStatus.FINISHED,
                service.getGame(game.id(), white()).status()
        );

        var moves = history();
        assertEquals(1, moves.size());

        var recorded = moves.getFirst();

        assertAll(
                () -> assertEquals(1L, recorded.getSequenceNumber()),
                () -> assertEquals(
                        expectedFen,
                        recorded.getResultingFen()
                ),
                () -> assertEquals(
                        persisted.getFinishedAt(),
                        recorded.getPlayedAt()
                ),
                () -> assertEquals(
                        1L,
                        persisted.getRecordedMoveCount()
                )
        );

        // Both players can now participate in another game.
        var nextGame = service.createGame(black(), white());

        // A retry against the old game must not release the new reservations.
        assertThrows(
                GameAlreadyFinishedException.class,
                () -> service.submitMove(
                        game.id(), mover(fen), 0, proposal
                )
        );

        assertEquals(
                nextGame.id(),
                service.findActiveGameId(white()).orElseThrow()
        );
        assertEquals(
                nextGame.id(),
                service.findActiveGameId(black()).orElseThrow()
        );
    }

    @Test
    void checkRemainsNonterminalAndRetainsReservations() {
        seedFen("7k/8/8/8/8/8/8/K3R3 w - - 4 20");

        var result = submit(white(), 0, "e1", "h1");

        assertEquals(PositionStatus.CHECK, result.positionStatus());
        assertEquals(GameStatus.IN_PROGRESS, result.status());
        assertNull(reload().getResult());
        assertReservations();
    }

    @Test
    void rejectedCommandsLeaveDurableStateUnchanged() {
        assertThrows(
                GameNotFoundException.class,
                () -> service.submitMove(
                        -1L, white(), 0, move("e2", "e4")
                )
        );
        assertThrows(
                GameAccessDeniedException.class,
                () -> submit(players.get(2).id(), 99, "e2", "e4")
        );
        assertThrows(
                StaleGameVersionException.class,
                () -> submit(white(), 99, "e2", "e4")
        );
        assertThrows(
                NotPlayersTurnException.class,
                () -> submit(black(), 0, "e7", "e5")
        );
        assertThrows(
                IllegalGameMoveException.class,
                () -> submit(white(), 0, "e2", "e5")
        );

        assertUnchanged(INITIAL);
        assertReservations();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Pinned knight.
            "4r2k/8/8/8/8/8/4N3/4K3 w - - 0 20 | e2 | c3",
            // En passant exposes the king.
            "7k/8/8/r4pPK/8/8/8/8 w - f6 0 20 | g5 | f6",
            // Castling through check.
            "k4r2/8/8/8/8/8/8/4K2R w K - 4 20 | e1 | g1",
            // Promotion choice is missing.
            "7k/4P3/8/8/8/8/8/K7 w - - 0 20 | e7 | e8"
    })
    void unsafeOrIncompleteProposalsDoNotPersist(
            String fen,
            String from,
            String to
    ) {
        seedFen(fen);

        assertThrows(
                IllegalGameMoveException.class,
                () -> submit(white(), 0, from, to)
        );

        assertUnchanged(fen);
        assertReservations();
    }

    @Test
    void invalidStoredPositionPropagatesItsValidationFailure() {
        // Deliberately invalid defensive fixture: Black king is missing.
        String invalid = "8/8/8/8/8/8/4N3/4K3 w - - 0 20";
        seedFen(invalid);

        assertThrows(
                IllegalArgumentException.class,
                () -> submit(white(), 0, "e2", "c3")
        );

        assertUnchanged(invalid);
        assertReservations();
    }

    @Test
    void oldVersionFailsEvenWhenTheSameMoveBecomesLegalAgain() {
        submit(white(), 0, "g1", "f3");
        submit(black(), 1, "g8", "f6");
        submit(white(), 2, "f3", "g1");
        var fourth = submit(black(), 3, "f6", "g8");

        assertThrows(
                StaleGameVersionException.class,
                () -> submit(white(), 0, "g1", "f3")
        );

        assertEquals(fourth.currentFen(), reload().getCurrentFen());
        assertEquals(Long.valueOf(4), reload().getVersion());
        assertReservations();
    }

    @Test
    void laterFailureRollsBackCompletionAndReservationRelease() {
        seedFen(BEFORE_WHITE_MATE);

        assertThrows(
                IllegalStateException.class,
                () -> new TransactionTemplate(transactionManager)
                        .executeWithoutResult(status -> {
                            var result = submit(
                                    white(), 0, "g6", "g7"
                            );

                            assertEquals(
                                    GameStatus.FINISHED, result.status()
                            );
                            assertEquals(0, reservationCount());

                            throw new IllegalStateException(
                                    "Simulated later transaction failure"
                            );
                        })
        );

        assertUnchanged(BEFORE_WHITE_MATE);
        assertReservations();
    }

    @Test
    void incompleteReservationReleaseRollsBackTheDeletionAndGame() {
        seedFen(BEFORE_WHITE_MATE);

        // Deliberately inconsistent fixture: Black's reservation is absent.
        jdbc.update(
                "DELETE FROM active_game_players WHERE player_id = ?",
                black()
        );

        assertThrows(
                IllegalStateException.class,
                () -> submit(white(), 0, "g6", "g7")
        );

        assertUnchanged(BEFORE_WHITE_MATE);

        // White's deleted reservation must have been restored by rollback.
        assertEquals(1, reservationCount());
        assertEquals(
                game.id(),
                service.findActiveGameId(white()).orElseThrow()
        );
        assertTrue(service.findActiveGameId(black()).isEmpty());
    }

    @Test
    void releasedPlayersAreNoLongerReportedAsMatched() {
        seedFen(BEFORE_WHITE_MATE);
        submit(white(), 0, "g6", "g7");

        assertEquals(
                QueueStatus.NOT_QUEUED,
                matchmaking.status(white()).status()
        );
        assertEquals(
                QueueStatus.NOT_QUEUED,
                matchmaking.status(black()).status()
        );
    }

    @Test
    void concurrentOrdinaryCommandsCommitExactlyOneMove()
            throws Exception {

        race(move("e2", "e4"), move("d2", "d4"));

        var persisted = reload();

        assertEquals(Long.valueOf(1), persisted.getVersion());
        assertTrue(Set.of(
                "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
                        + " b KQkq e3 0 1",
                "rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR"
                        + " b KQkq d3 0 1"
        ).contains(persisted.getCurrentFen()));

        assertReservations();
        assertExactlyOneRecordedMove(persisted);
    }

    @Test
    void concurrentTerminalCommandsCommitExactlyOneCompletion()
            throws Exception {

        seedFen(BEFORE_WHITE_MATE);

        race(move("g6", "g7"), move("g6", "g7"));

        var persisted = reload();

        assertEquals(Long.valueOf(1), persisted.getVersion());
        assertEquals(GameStatus.FINISHED, persisted.getStatus());
        assertEquals(GameResult.WHITE_WIN, persisted.getResult());
        assertEquals(0, reservationCount());
        assertExactlyOneRecordedMove(persisted);
    }

    @Test
    void databaseRejectsIncompleteOrInconsistentCompletion() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("""
                        UPDATE games
                        SET status = 'FINISHED'
                        WHERE id = ?
                        """, game.id())
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("""
                        UPDATE games
                        SET status = 'FINISHED',
                            result = 'DRAW',
                            termination_reason = 'CHECKMATE',
                            finished_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """, game.id())
        );

        assertUnchanged(INITIAL);
        assertReservations();
    }

    @Test
    void creationNotifiesBothPlayersAfterCommit() {
        // setUp() called createGame() outside a surrounding transaction,
        // so its service transaction has already committed.
        var update = new PlayerUpdate(
                "GAME_CREATED",
                game.id(),
                0
        );

        verify(outboundMessages).convertAndSendToUser(
                white().toString(),
                webSocketProperties.updatesDestination(),
                update
        );

        verify(outboundMessages).convertAndSendToUser(
                black().toString(),
                webSocketProperties.updatesDestination(),
                update
        );
    }

    @Test
    void moveNotifiesBothPlayersOnlyAfterTheOuterCommit() {
        clearInvocations(outboundMessages);

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    submit(white(), 0, "e2", "e4");

                    // The service returned and flushed, but the enclosing
                    // transaction has not committed.
                    verifyNoInteractions(outboundMessages);
                });

        var update = new PlayerUpdate(
                "GAME_UPDATED",
                game.id(),
                1
        );

        verify(outboundMessages).convertAndSendToUser(
                white().toString(),
                webSocketProperties.updatesDestination(),
                update
        );

        verify(outboundMessages).convertAndSendToUser(
                black().toString(),
                webSocketProperties.updatesDestination(),
                update
        );

        assertEquals(Long.valueOf(1), reload().getVersion());
    }

    @Test
    void rolledBackMoveDoesNotNotifyEitherPlayer() {
        clearInvocations(outboundMessages);

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    submit(white(), 0, "e2", "e4");

                    verifyNoInteractions(outboundMessages);
                    status.setRollbackOnly();
                });

        verifyNoInteractions(outboundMessages);
        assertUnchanged(INITIAL);
        assertReservations();
    }

    @Test
    void legacyGameRecordsForwardWithoutClaimingCompleteHistory() {
        String baseline =
                "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
                        + " b KQkq e3 0 1";

        // Simulate the state established by V7 for an existing game.
        // Earlier moves are unavailable; the next recorded sequence is one.
        assertEquals(
                1,
                jdbc.update("""
                    UPDATE games
                    SET current_fen = ?,
                        history_start_fen = ?,
                        history_complete = FALSE
                    WHERE id = ?
                    """,
                        baseline,
                        baseline,
                        game.id()
                )
        );

        var result = submit(black(), 0, "e7", "e5");
        var persisted = reload();
        var moves = history();

        assertEquals(1, moves.size());

        var recorded = moves.getFirst();

        assertAll(
                () -> assertFalse(persisted.isHistoryComplete()),
                () -> assertEquals(
                        baseline,
                        persisted.getHistoryStartFen()
                ),
                () -> assertEquals(
                        1L,
                        persisted.getRecordedMoveCount()
                ),
                () -> assertEquals(
                        1L,
                        recorded.getSequenceNumber()
                ),
                () -> assertEquals(
                        black(),
                        recorded.getPlayerId()
                ),
                () -> assertEquals(
                        result.currentFen(),
                        recorded.getResultingFen()
                )
        );
    }

    @Test
    void boundedHistoryReadExcludesMovesAfterItsCapturedSequence() {
        var first = submit(white(), 0, "e2", "e4");

        // Simulate capturing the history boundary before a later move commits.
        long capturedCount = reload().getRecordedMoveCount();

        submit(black(), 1, "e7", "e5");

        var bounded = moveRepository.findHistoryThrough(
                game.id(),
                capturedCount
        );

        assertEquals(1, bounded.size());
        assertEquals(1L, bounded.getFirst().getSequenceNumber());
        assertEquals(
                first.currentFen(),
                bounded.getFirst().getResultingFen()
        );

        assertEquals(2, history().size());
    }

    @ParameterizedTest
    @CsvSource({
            "true, BLACK_WIN",
            "false, WHITE_WIN"
    })
    void eitherParticipantCanResignWithoutRecordingAMove(
            boolean whiteResigns,
            GameResult expectedResult
    ) {
        // The initial position has White to move.
        // Black resignation therefore also verifies resignation out of turn.
        Long resigningPlayer = whiteResigns ? white() : black();

        var result = service.resign(
                game.id(),
                resigningPlayer,
                game.version()
        );

        var persisted = reload();

        assertAll(
                () -> assertEquals(GameStatus.FINISHED, result.status()),
                () -> assertEquals(expectedResult, result.result()),
                () -> assertEquals(
                        GameTerminationReason.RESIGNATION,
                        result.terminationReason()
                ),
                () -> assertNotNull(result.finishedAt()),
                () -> assertEquals(
                        Long.valueOf(game.version() + 1),
                        result.version()
                ),
                () -> assertEquals(
                        result.version(),
                        persisted.getVersion()
                ),
                () -> assertEquals(expectedResult, persisted.getResult()),
                () -> assertEquals(
                        GameTerminationReason.RESIGNATION,
                        persisted.getTerminationReason()
                ),
                () -> assertEquals(
                        result.finishedAt(),
                        persisted.getFinishedAt()
                ),
                () -> assertEquals(INITIAL, persisted.getCurrentFen()),
                () -> assertEquals(INITIAL, persisted.getHistoryStartFen()),
                () -> assertTrue(persisted.isHistoryComplete()),
                () -> assertEquals(0L, persisted.getRecordedMoveCount()),
                () -> assertTrue(history().isEmpty()),
                () -> assertEquals(0, reservationCount()),
                () -> assertTrue(
                        service.findActiveGameId(white()).isEmpty()
                ),
                () -> assertTrue(
                        service.findActiveGameId(black()).isEmpty()
                )
        );
    }

    @Test
    void resignationRejectsOutsidersAndStaleVersionsWithoutMutation() {
        Long outsider = players.get(2).id();

        // Membership takes precedence over a stale version.
        assertThrows(
                GameAccessDeniedException.class,
                () -> service.resign(game.id(), outsider, 99)
        );

        assertThrows(
                StaleGameVersionException.class,
                () -> service.resign(game.id(), white(), 99)
        );

        assertUnchanged(INITIAL);
        assertReservations();
    }

    @Test
    void finishedGameRejectsAnotherResignationBeforeVersionCheck() {
        service.resign(game.id(), white(), game.version());

        var completed = service.getGame(game.id(), black());

        assertThrows(
                GameAlreadyFinishedException.class,
                () -> service.resign(game.id(), black(), game.version())
        );

        assertEquals(completed, service.getGame(game.id(), black()));
        assertEquals(0, reservationCount());
        assertTrue(history().isEmpty());
    }

    @Test
    void laterFailureRollsBackResignationAndReservationRelease() {
        // Ignore creation notifications when checking this transaction.
        clearInvocations(outboundMessages);

        assertThrows(
                IllegalStateException.class,
                () -> new TransactionTemplate(transactionManager)
                        .executeWithoutResult(status -> {
                            var result = service.resign(
                                    game.id(),
                                    white(),
                                    game.version()
                            );

                            assertEquals(
                                    GameStatus.FINISHED,
                                    result.status()
                            );
                            assertEquals(0, reservationCount());

                            throw new IllegalStateException(
                                    "Simulated later transaction failure"
                            );
                        })
        );

        assertUnchanged(INITIAL);
        assertReservations();

        // The enclosing transaction rolled back, so no hint is delivered.
        verifyNoInteractions(outboundMessages);
    }

    @Test
    void incompleteReservationReleaseRollsBackResignation() {
        // Deliberately inconsistent fixture: Black's reservation is absent.
        jdbc.update(
                "DELETE FROM active_game_players WHERE player_id = ?",
                black()
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.resign(game.id(), white(), game.version())
        );

        assertUnchanged(INITIAL);

        // White's deletion must also have rolled back.
        assertEquals(1, reservationCount());
        assertEquals(
                game.id(),
                service.findActiveGameId(white()).orElseThrow()
        );
    }

    @Test
    void moveAndResignationCannotBothCommitFromTheSameVersion()
            throws Exception {
        race(
                () -> service.submitMove(
                        game.id(),
                        white(),
                        0,
                        move("e2", "e4")
                ),
                () -> service.resign(game.id(), black(), 0)
        );

        var persisted = reload();

        assertEquals(Long.valueOf(1), persisted.getVersion());

        if (persisted.getStatus() == GameStatus.FINISHED) {
            // Black's resignation won; the competing move rolled back.
            assertEquals(GameResult.WHITE_WIN, persisted.getResult());
            assertEquals(
                    GameTerminationReason.RESIGNATION,
                    persisted.getTerminationReason()
            );
            assertNotNull(persisted.getFinishedAt());
            assertEquals(INITIAL, persisted.getCurrentFen());
            assertEquals(0L, persisted.getRecordedMoveCount());
            assertTrue(history().isEmpty());
            assertEquals(0, reservationCount());
        } else {
            // White's move won; the competing resignation rolled back.
            assertEquals(GameStatus.IN_PROGRESS, persisted.getStatus());
            assertNull(persisted.getResult());
            assertNull(persisted.getTerminationReason());
            assertNull(persisted.getFinishedAt());
            assertEquals(
                    "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
                            + " b KQkq e3 0 1",
                    persisted.getCurrentFen()
            );
            assertExactlyOneRecordedMove(persisted);
            assertReservations();
        }
    }

    @Test
    void resignationNotifiesBothPlayersOnlyAfterTheOuterCommit() {
        clearInvocations(outboundMessages);

        var result = new TransactionTemplate(transactionManager)
                .execute(status -> {
                    var response = service.resign(
                            game.id(),
                            white(),
                            game.version()
                    );

                    // Flush and reservation release have happened,
                    // but the enclosing transaction has not committed.
                    verifyNoInteractions(outboundMessages);
                    return response;
                });

        assertNotNull(result);

        var expectedUpdate = new PlayerUpdate(
                GameChangedEvent.Type.GAME_UPDATED.name(),
                game.id(),
                result.version()
        );

        verify(outboundMessages).convertAndSendToUser(
                white().toString(),
                webSocketProperties.updatesDestination(),
                expectedUpdate
        );

        verify(outboundMessages).convertAndSendToUser(
                black().toString(),
                webSocketProperties.updatesDestination(),
                expectedUpdate
        );

        verifyNoMoreInteractions(outboundMessages);
    }

    @Test
    void agreedDrawFinishesWithoutRecordingAMove() {
        var offered = service.offerDraw(game.id(), white(), 0);

        assertEquals(Long.valueOf(1), offered.version());
        assertEquals(white(), offered.drawOfferedByPlayerId());
        assertEquals(GameStatus.IN_PROGRESS, offered.status());
        assertReservations();

        var accepted = service.acceptDraw(
                game.id(),
                black(),
                offered.version()
        );

        var persisted = reload();

        assertAll(
                () -> assertEquals(Long.valueOf(2), accepted.version()),
                () -> assertEquals(GameStatus.FINISHED, persisted.getStatus()),
                () -> assertEquals(GameResult.DRAW, persisted.getResult()),
                () -> assertEquals(
                        GameTerminationReason.DRAW_AGREEMENT,
                        persisted.getTerminationReason()
                ),
                () -> assertNotNull(persisted.getFinishedAt()),
                () -> assertNull(persisted.getDrawOfferedByPlayerId()),
                () -> assertEquals(INITIAL, persisted.getCurrentFen()),
                () -> assertEquals(0L, persisted.getRecordedMoveCount()),
                () -> assertTrue(history().isEmpty()),
                () -> assertEquals(0, reservationCount())
        );

        // History follows the game version even when no chess move was added.
        var recorded = service.getHistory(game.id(), white());

        assertEquals(accepted.version().longValue(), recorded.version());
        assertEquals(0L, recorded.recordedMoveCount());
        assertTrue(recorded.moves().isEmpty());
    }

    @Test
    void decliningAndWithdrawingOffersKeepReservations() {
        var offered = service.offerDraw(game.id(), white(), 0);

        var declined = service.declineDraw(
                game.id(),
                black(),
                offered.version()
        );

        assertEquals(Long.valueOf(2), declined.version());
        assertNull(declined.drawOfferedByPlayerId());
        assertEquals(GameStatus.IN_PROGRESS, declined.status());
        assertReservations();

        var secondOffer = service.offerDraw(
                game.id(),
                black(),
                declined.version()
        );

        var withdrawn = service.withdrawDraw(
                game.id(),
                black(),
                secondOffer.version()
        );

        assertEquals(Long.valueOf(4), withdrawn.version());
        assertNull(withdrawn.drawOfferedByPlayerId());
        assertEquals(GameStatus.IN_PROGRESS, withdrawn.status());
        assertEquals(INITIAL, withdrawn.currentFen());
        assertEquals(0L, reload().getRecordedMoveCount());
        assertTrue(history().isEmpty());
        assertReservations();
    }

    @Test
    void acceptedMoveClearsPendingOfferAndRecordsOnlyTheMove() {
        var offered = service.offerDraw(game.id(), black(), 0);

        var moved = submit(
                white(),
                offered.version(),
                "e2",
                "e4"
        );

        var persisted = reload();

        assertEquals(Long.valueOf(2), moved.version());
        assertNull(persisted.getDrawOfferedByPlayerId());
        assertEquals(1L, persisted.getRecordedMoveCount());
        assertEquals(1, history().size());
        assertEquals("e4", history().getFirst().getSan());
        assertReservations();

        assertThrows(
                DrawOfferNotFoundException.class,
                () -> service.acceptDraw(
                        game.id(),
                        white(),
                        moved.version()
                )
        );

        assertEquals(moved.version(), reload().getVersion());
    }

    @Test
    void rejectedMovePreservesPendingOffer() {
        var offered = service.offerDraw(game.id(), black(), 0);

        assertThrows(
                IllegalGameMoveException.class,
                () -> submit(
                        white(),
                        offered.version(),
                        "e2",
                        "e5"
                )
        );

        var persisted = reload();

        assertEquals(offered.version(), persisted.getVersion());
        assertEquals(black(), persisted.getDrawOfferedByPlayerId());
        assertEquals(INITIAL, persisted.getCurrentFen());
        assertEquals(0L, persisted.getRecordedMoveCount());
        assertTrue(history().isEmpty());
        assertReservations();
    }

    @Test
    void drawCommandsRequireAnOfferAndTheCorrectActor() {
        // No pending offer.
        assertThrows(
                DrawOfferNotFoundException.class,
                () -> service.acceptDraw(game.id(), black(), 0)
        );
        assertThrows(
                DrawOfferNotFoundException.class,
                () -> service.declineDraw(game.id(), black(), 0)
        );
        assertThrows(
                DrawOfferNotFoundException.class,
                () -> service.withdrawDraw(game.id(), white(), 0)
        );

        var offered = service.offerDraw(game.id(), white(), 0);
        long version = offered.version();

        assertThrows(
                DrawOfferAlreadyPendingException.class,
                () -> service.offerDraw(game.id(), black(), version)
        );

        // The sender cannot accept or decline their own offer.
        assertThrows(
                DrawOfferActionNotAllowedException.class,
                () -> service.acceptDraw(game.id(), white(), version)
        );
        assertThrows(
                DrawOfferActionNotAllowedException.class,
                () -> service.declineDraw(game.id(), white(), version)
        );

        // The recipient cannot withdraw someone else's offer.
        assertThrows(
                DrawOfferActionNotAllowedException.class,
                () -> service.withdrawDraw(game.id(), black(), version)
        );

        var persisted = reload();

        assertEquals(offered.version(), persisted.getVersion());
        assertEquals(white(), persisted.getDrawOfferedByPlayerId());
        assertEquals(GameStatus.IN_PROGRESS, persisted.getStatus());
        assertEquals(INITIAL, persisted.getCurrentFen());
        assertTrue(history().isEmpty());
        assertReservations();
    }

    @Test
    void acceptingDrawRollsBackWithTheEnclosingTransaction() {
        var offered = service.offerDraw(game.id(), white(), 0);
        clearInvocations(outboundMessages);

        assertThrows(
                IllegalStateException.class,
                () -> new TransactionTemplate(transactionManager)
                        .executeWithoutResult(status -> {
                            service.acceptDraw(
                                    game.id(),
                                    black(),
                                    offered.version()
                            );

                            assertEquals(0, reservationCount());

                            throw new IllegalStateException(
                                    "Simulated later transaction failure"
                            );
                        })
        );

        var persisted = reload();

        assertEquals(offered.version(), persisted.getVersion());
        assertEquals(white(), persisted.getDrawOfferedByPlayerId());
        assertEquals(GameStatus.IN_PROGRESS, persisted.getStatus());
        assertNull(persisted.getResult());
        assertNull(persisted.getTerminationReason());
        assertNull(persisted.getFinishedAt());
        assertEquals(0L, persisted.getRecordedMoveCount());
        assertTrue(history().isEmpty());
        assertReservations();
        verifyNoInteractions(outboundMessages);
    }

    @Test
    void incompleteReservationReleaseRollsBackDrawAcceptance() {
        var offered = service.offerDraw(game.id(), white(), 0);
        clearInvocations(outboundMessages);

        jdbc.update(
                "DELETE FROM active_game_players WHERE player_id = ?",
                black()
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.acceptDraw(
                        game.id(),
                        black(),
                        offered.version()
                )
        );

        var persisted = reload();

        assertEquals(offered.version(), persisted.getVersion());
        assertEquals(white(), persisted.getDrawOfferedByPlayerId());
        assertEquals(GameStatus.IN_PROGRESS, persisted.getStatus());
        assertNull(persisted.getResult());
        assertNull(persisted.getTerminationReason());
        assertNull(persisted.getFinishedAt());
        assertTrue(history().isEmpty());

        // The failed completion must restore White's deleted reservation.
        assertEquals(1, reservationCount());
        assertEquals(
                game.id(),
                service.findActiveGameId(white()).orElseThrow()
        );
        verifyNoInteractions(outboundMessages);
    }

    @Test
    void drawAcceptanceAndMoveCannotBothCommitFromTheSameVersion()
            throws Exception {
        var offered = service.offerDraw(game.id(), white(), 0);
        long version = offered.version();

        clearInvocations(outboundMessages);

        race(
                version,
                () -> service.acceptDraw(game.id(), black(), version),
                () -> service.submitMove(
                        game.id(),
                        white(),
                        version,
                        move("e2", "e4")
                )
        );

        var persisted = reload();

        assertEquals(
                Long.valueOf(version + 1),
                persisted.getVersion()
        );
        assertNull(persisted.getDrawOfferedByPlayerId());

        if (persisted.getStatus() == GameStatus.FINISHED) {
            // Acceptance won; the competing move and history insert rolled back.
            assertEquals(GameResult.DRAW, persisted.getResult());
            assertEquals(
                    GameTerminationReason.DRAW_AGREEMENT,
                    persisted.getTerminationReason()
            );
            assertNotNull(persisted.getFinishedAt());
            assertEquals(INITIAL, persisted.getCurrentFen());
            assertEquals(0L, persisted.getRecordedMoveCount());
            assertTrue(history().isEmpty());
            assertEquals(0, reservationCount());
        } else {
            // The move won; acceptance and reservation release rolled back.
            assertEquals(GameStatus.IN_PROGRESS, persisted.getStatus());
            assertNull(persisted.getResult());
            assertNull(persisted.getTerminationReason());
            assertNull(persisted.getFinishedAt());
            assertEquals(
                    "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
                            + " b KQkq e3 0 1",
                    persisted.getCurrentFen()
            );
            assertExactlyOneRecordedMove(persisted);
            assertReservations();
        }

        // Only the committed command delivers a change hint.
        var update = new PlayerUpdate(
                "GAME_UPDATED",
                game.id(),
                version + 1
        );

        verify(outboundMessages).convertAndSendToUser(
                white().toString(),
                webSocketProperties.updatesDestination(),
                update
        );
        verify(outboundMessages).convertAndSendToUser(
                black().toString(),
                webSocketProperties.updatesDestination(),
                update
        );
        verifyNoMoreInteractions(outboundMessages);
    }

    private void race(Move firstMove, Move secondMove)
            throws Exception {
        race(
                () -> service.submitMove(
                        game.id(), white(), 0, firstMove
                ),
                () -> service.submitMove(
                        game.id(), white(), 0, secondMove
                )
        );
    }

    private void race(Runnable firstCommand, Runnable secondCommand)
            throws Exception {
        race(0, firstCommand, secondCommand);
    }

    private void race(
            long expectedVersion,
            Runnable firstCommand,
            Runnable secondCommand
    ) throws Exception {
        var bothLoaded = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(
                    attempt(
                            firstCommand,
                            expectedVersion,
                            bothLoaded,
                            start
                    )
            );
            var second = executor.submit(
                    attempt(
                            secondCommand,
                            expectedVersion,
                            bothLoaded,
                            start
                    )
            );

            assertTrue(bothLoaded.await(10, TimeUnit.SECONDS));
            start.countDown();

            boolean firstCommitted = first.get(15, TimeUnit.SECONDS);
            boolean secondCommitted = second.get(15, TimeUnit.SECONDS);

            assertNotEquals(firstCommitted, secondCommitted);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private Callable<Boolean> attempt(
            Runnable command,
            long expectedVersion,
            CountDownLatch bothLoaded,
            CountDownLatch start
    ) {
        return () -> {
            try {
                Boolean committed = new TransactionTemplate(transactionManager)
                        .execute(status -> {
                            // Both transactions load the same version before
                            // either starts mutating its managed game.
                            var loaded = repository.findById(game.id())
                                    .orElseThrow();

                            assertEquals(
                                    Long.valueOf(expectedVersion),
                                    loaded.getVersion()
                            );

                            bothLoaded.countDown();

                            try {
                                if (!start.await(10, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException(
                                            "Start signal timed out"
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
            }
        };
    }

    private GameMoveResult submit(
            Long playerId,
            long version,
            String from,
            String to
    ) {
        return service.submitMove(
                game.id(), playerId, version, move(from, to)
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

    private Game reload() {
        // Outside an enclosing transaction, this uses a fresh JPA context.
        return repository.findById(game.id()).orElseThrow();
    }

    private void assertUnchanged(String fen) {
        var persisted = reload();

        assertAll(
                () -> assertEquals(fen, persisted.getCurrentFen()),
                () -> assertEquals(
                        Long.valueOf(0), persisted.getVersion()
                ),
                () -> assertEquals(
                        GameStatus.IN_PROGRESS, persisted.getStatus()
                ),
                () -> assertNull(persisted.getResult()),
                () -> assertNull(persisted.getTerminationReason()),
                () -> assertNull(persisted.getFinishedAt())
        );
        assertEquals(0L, persisted.getRecordedMoveCount());
        assertTrue(history().isEmpty());
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

    private void assertReservations() {
        assertEquals(2, reservationCount());
        assertEquals(
                game.id(),
                service.findActiveGameId(white()).orElseThrow()
        );
        assertEquals(
                game.id(),
                service.findActiveGameId(black()).orElseThrow()
        );
    }

    private Long white() {
        return players.getFirst().id();
    }

    private Long black() {
        return players.get(1).id();
    }

    private Long mover(String fen) {
        return fen.split(" ")[1].equals("w") ? white() : black();
    }

    private static Move move(String from, String to) {
        return move(from, to, "-");
    }

    private static Move move(
            String from,
            String to,
            String promotion
    ) {
        return new Move(
                Square.fromAlgebraic(from),
                Square.fromAlgebraic(to),
                promotion.equals("-")
                        ? Optional.empty()
                        : Optional.of(PieceType.valueOf(promotion))
        );
    }

    private PlayerResponse createPlayer() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16);

        return playerService.createPlayer(
                "move_" + suffix,
                suffix + "@example.com"
        );
    }

    private List<GameMove> history() {
        return moveRepository.findAllByGameIdOrderBySequenceNumberAsc(
                game.id()
        );
    }

    private void assertExactlyOneRecordedMove(Game persisted) {
        var moves = history();

        assertEquals(1, moves.size());
        assertEquals(1L, persisted.getRecordedMoveCount());

        var recorded = moves.getFirst();

        assertAll(
                () -> assertEquals(
                        1L,
                        recorded.getSequenceNumber()
                ),
                () -> assertEquals(
                        white(),
                        recorded.getPlayerId()
                ),
                () -> assertEquals(
                        persisted.getCurrentFen(),
                        recorded.getResultingFen()
                )
        );
    }
}