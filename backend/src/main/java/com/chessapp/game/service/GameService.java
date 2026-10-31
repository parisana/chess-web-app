package com.chessapp.game.service;

import com.chessapp.game.dto.GameHistoryResponse;
import com.chessapp.game.dto.GameMoveResponse;
import com.chessapp.game.dto.GameMoveResult;
import com.chessapp.game.dto.GameResponse;
import com.chessapp.game.entity.*;
import com.chessapp.game.exception.*;
import com.chessapp.game.repository.GameMoveRepository;
import com.chessapp.game.repository.GameRepository;
import com.chessapp.game.rules.ChessRules;
import com.chessapp.game.rules.evaluation.PositionStatus;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.fen.FenSerializer;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.PieceColor;
import com.chessapp.game.rules.notation.SanFormatter;
import com.chessapp.player.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chessapp.game.event.GameChangedEvent;
import org.springframework.context.ApplicationEventPublisher;

import com.chessapp.game.repository.ActiveGamePlayerRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * @author pari on 10/22/26
 */
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;
    private final GameMoveRepository gameMoveRepository;
    private final PlayerService playerService;
    private final ActiveGamePlayerRepository activeGamePlayerRepository;
    private final ChessRules chessRules;
    private final FenSerializer fenSerializer;
    private final FenParser fenParser;
    private final SanFormatter sanFormatter;
    private final Clock clock;

    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public GameResponse getGame(Long gameId, Long requestingPlayerId) {
        var game = gameRepository.findById(gameId)
                .orElseThrow(GameNotFoundException::new);

        // Enforce access here so callers cannot bypass participant checks
        // by invoking the service outside the HTTP controller.
        boolean isParticipant =
                game.getWhitePlayerId().equals(requestingPlayerId)
                        || game.getBlackPlayerId().equals(requestingPlayerId);

        if (!isParticipant) {
            throw new GameAccessDeniedException();
        }

        return GameResponse.from(game);
    }

    @Transactional
    public GameResponse createGame(
            Long whitePlayerId,
            Long blackPlayerId
    ) {
        var initialPosition = chessRules.initialPosition();
        var initialFen = fenSerializer.serialize(initialPosition);

        var game = new Game(
                whitePlayerId,
                blackPlayerId,
                initialFen
        );

        requirePlayer(whitePlayerId);
        requirePlayer(blackPlayerId);

        // saveAndFlush ensures the game row exists before JDBC inserts rows referencing it. Flushing does not commit.
        // With our shared datasource and JPA transaction manager, JdbcTemplate participates in the same transaction.
        var savedGame = gameRepository.saveAndFlush(game);

        int reservedPlayers = activeGamePlayerRepository.reservePlayers(
                savedGame.getId(),
                whitePlayerId,
                blackPlayerId
        );

        if (reservedPlayers != 2) {
            throw new PlayerAlreadyInGameException();
        }
        publishGameChanged(
                savedGame,
                GameChangedEvent.Type.GAME_CREATED
        );
        return GameResponse.from(savedGame);
    }

    @Transactional(readOnly = true)
    public Optional<Long> findActiveGameId(Long playerId) {
        return activeGamePlayerRepository.findGameIdByPlayerId(playerId);
    }

    private void requirePlayer(Long playerId) {
        playerService.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Player does not exist: " + playerId
                ));
    }

    /**
     * Authorize, validate, apply and assess, then persist.
     * Terminal moves release reservations in the same transaction.
     */
    @Transactional
    public GameMoveResult submitMove(
            Long gameId,
            Long requestingPlayerId,
            long expectedVersion,
            Move move
    ) {
        Objects.requireNonNull(gameId, "Game ID is required");
        Objects.requireNonNull(
                requestingPlayerId,
                "Requesting player is required"
        );
        Objects.requireNonNull(move, "Move is required");

        if (expectedVersion < 0) {
            throw new IllegalArgumentException(
                    "Expected version must not be negative"
            );
        }

        var game = gameRepository.findById(gameId)
                .orElseThrow(GameNotFoundException::new);

        boolean isWhite =
                game.getWhitePlayerId().equals(requestingPlayerId);

        boolean isBlack =
                game.getBlackPlayerId().equals(requestingPlayerId);

        // Check access before revealing lifecycle, version or turn failures.
        if (!isWhite && !isBlack) {
            throw new GameAccessDeniedException();
        }

        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            throw new GameAlreadyFinishedException();
        }

        if (game.getVersion() != expectedVersion) {
            throw new StaleGameVersionException();
        }

        var position = fenParser.parse(game.getCurrentFen());

        var playerColor = isWhite
                ? PieceColor.WHITE
                : PieceColor.BLACK;

        if (position.sideToMove() != playerColor) {
            throw new NotPlayersTurnException();
        }

        final Set<Move> legalMoves = chessRules.legalMoves(position);
        // Distinguish an ordinary rejected proposal from invalid stored state
        // or other failures propagated by the rules pipeline.
        if (!legalMoves.contains(move)) {
            throw new IllegalGameMoveException();
        }

        // Application still goes through the legality-enforcing facade.
        var nextPosition = chessRules.applyMove(position, move);
        var assessment = chessRules.evaluate(nextPosition);
        var nextFen = fenSerializer.serialize(nextPosition);
        // Obtain one timestamp for the accepted move and any resulting completion.
        var playedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        // Notation needs the original position and complete legal alternatives.
        // Generate it before mutating the managed game.
        var san = sanFormatter.format(
                position,
                move,
                legalMoves,
                assessment
        );
        long nextSequenceNumber = updateGameForAcceptedMove(
                game,
                nextFen,
                assessment,
                playerColor,
                playedAt
        );

        // Resolve competing commands before attempting the history insert.
        flushGameOrRejectConflict();

        // We explicitly flush history so a persistence failure surfaces before reservation release and event publication.
        gameMoveRepository.saveAndFlush(
                new GameMove(
                        game.getId(),
                        nextSequenceNumber,
                        requestingPlayerId,
                        move,
                        nextFen,
                        playedAt,
                        san
                )
        );

        releaseReservationsIfFinished(game);

        publishGameChanged(game, GameChangedEvent.Type.GAME_UPDATED);

        // All writes still belong to this transaction.
        // Notifications are delivered only after its enclosing transaction commits.
        return GameMoveResult.from(game, assessment);
    }

    /**
     * Ends an active game by voluntary resignation.
     * Either participant may resign, regardless of the side to move.
     */
    @Transactional
    public GameResponse resign(
            Long gameId,
            Long requestingPlayerId,
            long expectedVersion
    ) {
        var game = loadActiveGameForCommand(
                gameId,
                requestingPlayerId,
                expectedVersion
        );

        boolean isWhite =
                game.getWhitePlayerId().equals(requestingPlayerId);

        game.finish(
                isWhite ? GameResult.BLACK_WIN : GameResult.WHITE_WIN,
                GameTerminationReason.RESIGNATION,
                clock.instant().truncatedTo(ChronoUnit.MICROS)
        );

        return persistLifecycleChange(game);
    }

    @Transactional(readOnly = true)
    public GameHistoryResponse getHistory(
            Long gameId,
            Long requestingPlayerId
    ) {
        var game = gameRepository.findById(gameId)
                .orElseThrow(GameNotFoundException::new);

        // Authorize before querying or exposing history.
        boolean isParticipant =
                game.getWhitePlayerId().equals(requestingPlayerId)
                        || game.getBlackPlayerId().equals(requestingPlayerId);

        if (!isParticipant) {
            throw new GameAccessDeniedException();
        }

        // Capture the boundary before querying the append-only move records.
        long recordedMoveCount = game.getRecordedMoveCount();

        var moves = gameMoveRepository.findHistoryThrough(
                        game.getId(),
                        recordedMoveCount
                )
                .stream()
                .map(GameMoveResponse::from)
                .toList();

        return new GameHistoryResponse(
                game.getId(),
                game.getVersion(),
                game.getHistoryStartFen(),
                game.isHistoryComplete(),
                recordedMoveCount,
                moves
        );
    }

    /**
     * Creates one pending offer without changing the board or move history.
     * Offering is permitted regardless of whose turn it is.
     */
    @Transactional
    public GameResponse offerDraw(
            Long gameId,
            Long requestingPlayerId,
            long expectedVersion
    ) {
        var game = loadActiveGameForCommand(
                gameId,
                requestingPlayerId,
                expectedVersion
        );

        if (game.getDrawOfferedByPlayerId() != null) {
            throw new DrawOfferAlreadyPendingException();
        }

        game.offerDraw(requestingPlayerId);

        return persistLifecycleChange(game);
    }

    /**
     * Accepts the opponent's pending offer and releases both reservations
     * in the same transaction as completion.
     */
    @Transactional
    public GameResponse acceptDraw(
            Long gameId,
            Long requestingPlayerId,
            long expectedVersion
    ) {
        var game = loadActiveGameForCommand(
                gameId,
                requestingPlayerId,
                expectedVersion
        );

        requireDrawOfferActor(game, requestingPlayerId, false);

        game.finish(
                GameResult.DRAW,
                GameTerminationReason.DRAW_AGREEMENT,
                clock.instant().truncatedTo(ChronoUnit.MICROS)
        );

        return persistLifecycleChange(game);
    }

    /**
     * Declines the opponent's offer while keeping the game active.
     */
    @Transactional
    public GameResponse declineDraw(
            Long gameId,
            Long requestingPlayerId,
            long expectedVersion
    ) {
        var game = loadActiveGameForCommand(
                gameId,
                requestingPlayerId,
                expectedVersion
        );

        requireDrawOfferActor(game, requestingPlayerId, false);

        game.clearDrawOffer();

        return persistLifecycleChange(game);
    }

    /**
     * Withdraws the acting Player's own offer while keeping the game active.
     */
    @Transactional
    public GameResponse withdrawDraw(
            Long gameId,
            Long requestingPlayerId,
            long expectedVersion
    ) {
        var game = loadActiveGameForCommand(
                gameId,
                requestingPlayerId,
                expectedVersion
        );

        requireDrawOfferActor(game, requestingPlayerId, true);

        game.clearDrawOffer();

        return persistLifecycleChange(game);
    }

    /**
     * Applies already-validated state and records any resulting completion.
     * The sequence is relative to this game's history baseline.
     * @return the sequenceNumber after the move is made
     */
    private long updateGameForAcceptedMove(
            Game game,
            String nextFen,
            PositionStatus assessment,
            PieceColor movingColor,
            Instant playedAt
    ) {
        // Complete rules processing before mutating the managed entity.
        long sequenceNumber = game.advanceRecordedMoveCount();
        game.updatePosition(nextFen);

        if (assessment == PositionStatus.CHECKMATE) {
            game.finish(
                    movingColor == PieceColor.WHITE
                            ? GameResult.WHITE_WIN
                            : GameResult.BLACK_WIN,
                    GameTerminationReason.CHECKMATE,
                    playedAt
            );
        } else if (assessment == PositionStatus.STALEMATE) {
            game.finish(
                    GameResult.DRAW,
                    GameTerminationReason.STALEMATE,
                    playedAt
            );
        }

        return sequenceNumber;
    }

    /**
     * Detects optimistic conflicts before history insertion or reservation release.
     * Flushing does not commit the transaction.
     */
    private void flushGameOrRejectConflict() {
        try {
            gameRepository.flush();
        } catch (OptimisticLockingFailureException exception) {
            throw new StaleGameVersionException(exception);
        }
    }

    private void releaseReservationsIfFinished(Game game) {
        if (game.getStatus() != GameStatus.FINISHED) {
            return;
        }

        int released =
                activeGamePlayerRepository.releasePlayers(game.getId());

        if (released != 2) {
            throw new IllegalStateException(
                    "Finishing a game requires exactly two active reservations"
            );
        }
    }

    /**
     * Publishes an internal event now; its notification listener runs after commit.
     */
    private void publishGameChanged(
            Game game,
            GameChangedEvent.Type type
    ) {
        eventPublisher.publishEvent(
                new GameChangedEvent(
                        game.getId(),
                        game.getWhitePlayerId(),
                        game.getBlackPlayerId(),
                        game.getVersion(),
                        type
                )
        );
    }

    /**
     * Shared preconditions for explicit lifecycle commands.
     * Membership is checked before revealing lifecycle or version failures.
     */
    private Game loadActiveGameForCommand(
            Long gameId,
            Long requestingPlayerId,
            long expectedVersion
    ) {
        Objects.requireNonNull(gameId, "Game ID is required");
        Objects.requireNonNull(
                requestingPlayerId,
                "Requesting player is required"
        );

        if (expectedVersion < 0) {
            throw new IllegalArgumentException(
                    "Expected version must not be negative"
            );
        }

        var game = gameRepository.findById(gameId)
                .orElseThrow(GameNotFoundException::new);

        boolean isParticipant =
                game.getWhitePlayerId().equals(requestingPlayerId)
                        || game.getBlackPlayerId().equals(requestingPlayerId);

        if (!isParticipant) {
            throw new GameAccessDeniedException();
        }

        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            throw new GameAlreadyFinishedException();
        }

        if (game.getVersion() != expectedVersion) {
            throw new StaleGameVersionException();
        }

        return game;
    }

    /**
     * Flushes a lifecycle change before releasing reservations or publishing.
     * The public command owns the transaction; this helper does not commit.
     */
    private GameResponse persistLifecycleChange(Game game) {
        flushGameOrRejectConflict();
        releaseReservationsIfFinished(game);
        publishGameChanged(game, GameChangedEvent.Type.GAME_UPDATED);

        return GameResponse.from(game);
    }

    /**
     * Requires an existing offer and the correct participant for the action.
     */
    private void requireDrawOfferActor(
            Game game,
            Long requestingPlayerId,
            boolean mustBeSender
    ) {
        Long offeringPlayerId = game.getDrawOfferedByPlayerId();

        if (offeringPlayerId == null) {
            throw new DrawOfferNotFoundException();
        }

        boolean isSender = offeringPlayerId.equals(requestingPlayerId);

        if (isSender != mustBeSender) {
            throw new DrawOfferActionNotAllowedException();
        }
    }
}
