package com.chessapp.game.service;

import com.chessapp.game.clock.GameClockCalculator;
import com.chessapp.game.config.GameClockProperties;
import com.chessapp.game.dto.GameMoveResult;
import com.chessapp.game.dto.GameResponse;
import com.chessapp.game.entity.*;
import com.chessapp.game.event.GameChangedEvent;
import com.chessapp.game.event.GameFinishedEvent;
import com.chessapp.game.exception.*;
import com.chessapp.game.repository.ActiveGamePlayerRepository;
import com.chessapp.game.repository.GameMoveRepository;
import com.chessapp.game.repository.GameRepository;
import com.chessapp.game.rules.ChessRules;
import com.chessapp.game.rules.evaluation.PositionStatus;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.fen.FenSerializer;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.PieceColor;
import com.chessapp.game.rules.notation.SanFormatter;
import com.chessapp.player.dto.PlayerGameDetails;
import com.chessapp.player.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;

/**
 * @author pari on 10/22/26
 */
@Service
@RequiredArgsConstructor
public class GameCommandService {

    private final GameRepository gameRepository;
    private final GameMoveRepository gameMoveRepository;
    private final PlayerService playerService;
    private final ActiveGamePlayerRepository activeGamePlayerRepository;
    private final ChessRules chessRules;
    private final FenSerializer fenSerializer;
    private final FenParser fenParser;
    private final SanFormatter sanFormatter;
    private final Clock clock;

    private final GameClockProperties gameClockProperties;

    private final ApplicationEventPublisher eventPublisher;

    /**
     * Rechecks and completes one expired game in its own service transaction.

     * Candidate selection is only a hint: a move may have changed the clock
     * since selection, or another worker may already have finished the game.
     */
    @Transactional
    public boolean expireGame(Long gameId) {
        var game = gameRepository.findById(gameId)
                .orElseThrow(GameNotFoundException::new);

        if (game.getStatus() != GameStatus.IN_PROGRESS
                || !game.hasClock()) {
            return false;
        }

        var state = game.runningClockState();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

        if (!GameClockCalculator.isExpired(state, now)) {
            return false;
        }

        GameResult result = state.runningColor() == PieceColor.WHITE
                ? GameResult.BLACK_WIN
                : GameResult.WHITE_WIN;

        // Record the actual expiry instant rather than the later scan time.
        // finish() freezes the expired balance at zero.
        game.finish(
                result,
                GameTerminationReason.TIMEOUT,
                GameClockCalculator.deadline(state)
        );

        // Reuses optimistic conflict detection, reservation release,
        // synchronous statistics and after-commit notification publication.
        persistLifecycleChange(game);

        return true;
    }

    @Transactional
    public GameResponse createGame(Long whitePlayerId, Long blackPlayerId) {
        var initialPosition = chessRules.initialPosition();
        var initialFen = fenSerializer.serialize(initialPosition);

        var game = new Game(
                whitePlayerId,
                blackPlayerId,
                initialFen
        );

        var white = requirePlayer(whitePlayerId);
        var black = requirePlayer(blackPlayerId);

        game.captureParticipantDetails(
                white.username(),
                black.username(),
                white.rating(),
                black.rating()
        );

        game.startClock(
                gameClockProperties.initialTimeMillis(),
                clock.instant().truncatedTo(ChronoUnit.MICROS)
        );

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
                GameChangedEvent.Type.GAME_CREATED, null
        );
        return GameResponse.from(savedGame, clock.instant());
    }

    private PlayerGameDetails requirePlayer(Long playerId) {
        return playerService.findGameDetails(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player does not exist: " + playerId));
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
        boolean isWhite = game.getWhitePlayerId().equals(requestingPlayerId);
        boolean isBlack = game.getBlackPlayerId().equals(requestingPlayerId);
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
        // Access, lifecycle and version have now been checked.
        if (game.hasClock()) {
            captureCommandTimeAndCheckExpiry(game);
        }
        var position = fenParser.parse(game.getCurrentFen());

        if (game.hasClock() && game.getClockRunningColor() != position.sideToMove()) {
            throw new IllegalStateException(
                    "Running clock must match the position's side to move"
            );
        }

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
        var playedAt = captureCommandTimeAndCheckExpiry(game);
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
        publishGameFinishedIfNecessary(game); // handles updating of ratings synchronously
        final Instant calculatedAt = clock.instant();
        publishGameChanged(game, GameChangedEvent.Type.GAME_UPDATED, GameResponse.from(game, calculatedAt));

        // All writes still belong to this transaction.
        // Notifications are delivered only after its enclosing transaction commits.
        return GameMoveResult.from(game, assessment, calculatedAt);
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
                captureCommandTimeAndCheckExpiry(game)
        );

        return persistLifecycleChange(game);
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
                captureCommandTimeAndCheckExpiry(game)
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
        // Use the same timestamp for clock transition, history and completion.
        game.advanceClockAfterAcceptedMove(playedAt);
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
            GameChangedEvent.Type type,
            GameResponse grSnapshot
    ) {
        eventPublisher.publishEvent(new GameChangedEvent(
                game.getId(),
                game.getWhitePlayerId(),
                game.getBlackPlayerId(),
                game.getVersion(),
                type,
                grSnapshot
        ));
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

        if (game.hasClock()) { // check clock not expired for current player
            captureCommandTimeAndCheckExpiry(game);
        }

        return game;
    }

    /**
     * Flushes a lifecycle change before releasing reservations or publishing.
     * The public command owns the transaction; this helper does not commit.
     */
    private GameResponse persistLifecycleChange(Game game) {
        // Negotiation does not pause or reset the running clock.
        // Recheck before accepting a non-terminal change.
        if (game.getStatus() == GameStatus.IN_PROGRESS
                && game.hasClock()) {
            captureCommandTimeAndCheckExpiry(game);
        }
        flushGameOrRejectConflict();
        releaseReservationsIfFinished(game);
        publishGameFinishedIfNecessary(game); // handles updating player ratings synchronously using spring-events
        var snapshot = GameResponse.from(game, clock.instant());
        publishGameChanged(game, GameChangedEvent.Type.GAME_UPDATED, snapshot);

        return snapshot;
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

    /**
     * Captures server time for a command and rejects an expired timed game.
     * Rejection does not finish the game; the timeout worker owns that path.
     */
    private Instant captureCommandTimeAndCheckExpiry(Game game) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

        requireClockNotExpired(game, now);

        return now;
    }

    private void requireClockNotExpired(Game game, Instant now) {
        if (game.hasClock()
                && GameClockCalculator.isExpired(
                game.runningClockState(),
                now
        )) {
            throw new GameClockExpiredException();
        }
    }

    /**
     * Publishes only after the game writes and reservation release succeed.
     * Synchronous statistics listeners still participate in this transaction.
     */
    private void publishGameFinishedIfNecessary(Game game) {
        if (game.getStatus() != GameStatus.FINISHED) {
            return;
        }

        eventPublisher.publishEvent(
                new GameFinishedEvent(
                        game.getId(),
                        game.getWhitePlayerId(),
                        game.getBlackPlayerId(),
                        game.getResult()
                )
        );
    }
}
