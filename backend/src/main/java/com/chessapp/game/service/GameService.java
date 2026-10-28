package com.chessapp.game.service;

import com.chessapp.game.dto.GameMoveResult;
import com.chessapp.game.dto.GameResponse;
import com.chessapp.game.entity.Game;
import com.chessapp.game.entity.GameResult;
import com.chessapp.game.entity.GameStatus;
import com.chessapp.game.entity.GameTerminationReason;
import com.chessapp.game.exception.*;
import com.chessapp.game.repository.GameRepository;
import com.chessapp.game.rules.ChessRules;
import com.chessapp.game.rules.evaluation.PositionStatus;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.fen.FenSerializer;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.PieceColor;
import com.chessapp.player.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chessapp.game.repository.ActiveGamePlayerRepository;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * @author pari on 10/22/26
 */
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;
    private final PlayerService playerService;
    private final ActiveGamePlayerRepository activeGamePlayerRepository;
    private final ChessRules chessRules;
    private final FenSerializer fenSerializer;
    private final FenParser fenParser;
    private final Clock clock;

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

        return toResponse(game);
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

        return toResponse(savedGame);
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

        // Distinguish an ordinary rejected proposal from invalid stored state
        // or other failures propagated by the rules pipeline.
        if (!chessRules.legalMoves(position).contains(move)) {
            throw new IllegalGameMoveException();
        }

        // Application still goes through the legality-enforcing facade.
        var nextPosition = chessRules.applyMove(position, move);
        var assessment = chessRules.evaluate(nextPosition);
        var nextFen = fenSerializer.serialize(nextPosition);

        // Complete rules processing before mutating the managed entity.
        game.updatePosition(nextFen);

        if (assessment == PositionStatus.CHECKMATE) {
            game.finish(
                    isWhite
                            ? GameResult.WHITE_WIN
                            : GameResult.BLACK_WIN,
                    GameTerminationReason.CHECKMATE,
                    clock.instant().truncatedTo(ChronoUnit.MICROS)
            );
        } else if (assessment == PositionStatus.STALEMATE) {
            game.finish(
                    GameResult.DRAW,
                    GameTerminationReason.STALEMATE,
                    clock.instant().truncatedTo(ChronoUnit.MICROS)
            );
        }

        try {
            // Two transactions may both pass the expected-version check.
            // Flush rejects the losing update before reservation release.
            gameRepository.flush();
        } catch (OptimisticLockingFailureException exception) {
            throw new StaleGameVersionException(exception);
        }

        if (game.getStatus() == GameStatus.FINISHED) {
            int released =
                    activeGamePlayerRepository.releasePlayers(game.getId());

            if (released != 2) {
                throw new IllegalStateException(
                        "Finishing a game requires exactly two active reservations"
                );
            }
        }

        // Flush is not commit. An enclosing transaction can still roll back
        // the game update and reservation deletion together.
        return GameMoveResult.from(game, assessment);
    }


    private static GameResponse toResponse(Game game) {
        return new GameResponse(
                game.getId(),
                game.getWhitePlayerId(),
                game.getBlackPlayerId(),
                game.getStatus(),
                game.getCurrentFen(),
                game.getCreatedAt()
        );
    }
}
