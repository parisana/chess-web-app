package com.chessapp.game.service;

import com.chessapp.game.entity.Game;
import com.chessapp.game.entity.GameResult;
import com.chessapp.game.entity.GameTerminationReason;
import com.chessapp.game.exception.*;
import com.chessapp.game.repository.ActiveGamePlayerRepository;
import com.chessapp.game.repository.GameRepository;
import com.chessapp.game.rules.ChessRules;
import com.chessapp.game.rules.evaluation.PositionStatus;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.fen.FenSerializer;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.Square;
import com.chessapp.player.service.PlayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameMoveServiceTest {

    private static final String INITIAL =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                    + " w KQkq - 0 1";

    private static final String AFTER_E4 =
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
                    + " b KQkq e3 0 1";

    private static final Move E4 = move("e2", "e4");

    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private GameRepository gameRepository;
    @Mock private PlayerService playerService;
    @Mock private ActiveGamePlayerRepository reservations;
    @Mock private ChessRules rules;
    @Mock private FenParser parser;
    @Mock private FenSerializer serializer;
    @Mock private Clock clock;

    @InjectMocks
    private GameService service;

    private Game game;

    @BeforeEach
    void setUp() {
        game = storedGame(INITIAL);
    }

    @Test
    void missingGameHasItsOwnFailure() {
        when(gameRepository.findById(10L))
                .thenReturn(Optional.empty());

        assertThrows(
                GameNotFoundException.class,
                () -> service.submitMove(10L, 1L, 0, E4)
        );

        verifyNoInteractions(parser, rules, reservations);
    }

    @Test
    void outsiderIsRejectedBeforeLifecycleAndVersionChecks() {
        game.finish(
                GameResult.DRAW,
                GameTerminationReason.STALEMATE,
                Instant.EPOCH
        );
        found();

        assertThrows(
                GameAccessDeniedException.class,
                () -> service.submitMove(10L, 3L, 99, E4)
        );

        verifyNoProcessing();
    }

    @Test
    void finishedGameIsRejectedBeforeStaleVersion() {
        game.finish(
                GameResult.DRAW,
                GameTerminationReason.STALEMATE,
                Instant.EPOCH
        );
        found();

        assertThrows(
                GameAlreadyFinishedException.class,
                () -> service.submitMove(10L, 1L, 99, E4)
        );

        verifyNoProcessing();
    }

    @Test
    void staleVersionIsRejectedBeforeTurnChecks() {
        found();

        assertThrows(
                StaleGameVersionException.class,
                () -> service.submitMove(10L, 2L, 99, E4)
        );

        verifyNoProcessing();
    }

    @Test
    void participantCannotMoveOnTheOpponentsTurn() {
        found();
        parsedPosition();

        assertThrows(
                NotPlayersTurnException.class,
                () -> service.submitMove(10L, 2L, 0, E4)
        );

        verifyNoInteractions(rules, reservations);
        verify(gameRepository, never()).flush();
    }

    @Test
    void absentLegalProposalIsAnApplicationRejection() {
        found();
        var position = parsedPosition();
        when(rules.legalMoves(position)).thenReturn(Set.of());

        assertThrows(
                IllegalGameMoveException.class,
                () -> service.submitMove(10L, 1L, 0, E4)
        );

        verify(rules, never()).applyMove(any(), any());
        assertEquals(INITIAL, game.getCurrentFen());
        verify(gameRepository, never()).flush();
        verifyNoInteractions(reservations);
    }

    @Test
    void generationFailureIsNotDisguisedAsAnIllegalProposal() {
        found();
        var position = parsedPosition();
        var failure = new IllegalArgumentException(
                "Invalid stored position"
        );
        when(rules.legalMoves(position)).thenThrow(failure);

        assertSame(
                failure,
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.submitMove(10L, 1L, 0, E4)
                )
        );

        assertEquals(INITIAL, game.getCurrentFen());
        verify(gameRepository, never()).flush();
        verifyNoInteractions(reservations);
    }

    @Test
    void assessmentFailureOccursBeforeEntityMutation() {
        found();
        var position = parsedPosition();
        var next = new FenParser().parse(AFTER_E4);

        when(rules.legalMoves(position)).thenReturn(Set.of(E4));
        when(rules.applyMove(position, E4)).thenReturn(next);

        var failure = new UnsupportedOperationException(
                "Incomplete rules configuration"
        );
        when(rules.evaluate(next)).thenThrow(failure);

        assertSame(
                failure,
                assertThrows(
                        UnsupportedOperationException.class,
                        () -> service.submitMove(10L, 1L, 0, E4)
                )
        );

        assertEquals(INITIAL, game.getCurrentFen());
        verify(gameRepository, never()).flush();
        verifyNoInteractions(serializer, reservations);
    }

    @Test
    void optimisticConflictPreventsReleaseAndRetainsItsCause() {
        String beforeMate =
                "7k/8/5KQ1/8/8/8/8/8 w - - 4 20";
        String afterMate =
                "7k/6Q1/5K2/8/8/8/8/8 b - - 5 20";

        game = storedGame(beforeMate);
        found();

        var position = parsedPosition();
        var next = new FenParser().parse(afterMate);
        var proposal = move("g6", "g7");

        when(rules.legalMoves(position)).thenReturn(Set.of(proposal));
        when(rules.applyMove(position, proposal)).thenReturn(next);
        when(rules.evaluate(next)).thenReturn(PositionStatus.CHECKMATE);
        when(serializer.serialize(next)).thenReturn(afterMate);
        when(clock.instant()).thenReturn(
                Instant.parse("2026-10-28T10:00:00Z")
        );

        var failure = new OptimisticLockingFailureException(
                "Competing update"
        );
        doThrow(failure).when(gameRepository).flush();

        var conflict = assertThrows(
                StaleGameVersionException.class,
                () -> service.submitMove(10L, 1L, 0, proposal)
        );

        assertSame(failure, conflict.getCause());
        verifyNoInteractions(reservations);

        // Mock-only tests establish ordering, not database rollback.
    }

    private void found() {
        when(gameRepository.findById(10L))
                .thenReturn(Optional.of(game));
    }

    private com.chessapp.game.rules.model.ChessPosition parsedPosition() {
        var position = new FenParser().parse(game.getCurrentFen());
        when(parser.parse(game.getCurrentFen())).thenReturn(position);
        return position;
    }

    private void verifyNoProcessing() {
        verifyNoInteractions(parser, rules, serializer, reservations);
        verify(gameRepository, never()).flush();
    }

    private static Game storedGame(String fen) {
        var game = new Game(1L, 2L, fen);

        // Supply persistence-generated fields for this isolated unit fixture.
        ReflectionTestUtils.setField(game, "id", 10L);
        ReflectionTestUtils.setField(game, "version", 0L);

        return game;
    }

    private static Move move(String from, String to) {
        return new Move(
                Square.fromAlgebraic(from),
                Square.fromAlgebraic(to)
        );
    }
}