package com.chessapp.game.service;

import com.chessapp.game.dto.*;
import com.chessapp.game.entity.Game;
import com.chessapp.game.entity.GameStatus;
import com.chessapp.game.exception.GameAccessDeniedException;
import com.chessapp.game.exception.GameNotFoundException;
import com.chessapp.game.repository.ActiveGamePlayerRepository;
import com.chessapp.game.repository.GameMoveRepository;
import com.chessapp.game.repository.GameRepository;
import com.chessapp.game.rules.ChessRules;
import com.chessapp.game.rules.fen.FenParser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * @author pari on 11/5/26
 */
@Service
@RequiredArgsConstructor
public class GameQueryService {

    private final GameRepository gameRepository;
    private final GameMoveRepository gameMoveRepository;
    private final ActiveGamePlayerRepository activeGamePlayerRepository;
    private final ChessRules chessRules;
    private final FenParser fenParser;
    private final Clock clock;

    @Transactional(readOnly = true)
    public GameResponse getGame(Long gameId, Long requestingPlayerId) {
        var game = loadGameForParticipant(gameId, requestingPlayerId);
        return GameResponse.from(game, clock.instant());
    }

    @Transactional(readOnly = true)
    public GameHistoryResponse getHistory(Long gameId, Long requestingPlayerId) {
        var game = loadGameForParticipant(gameId, requestingPlayerId);
        // Capture the boundary before querying append-only move records.
        long recordedMoveCount = game.getRecordedMoveCount();

        var moves = gameMoveRepository.findHistoryThrough(game.getId(), recordedMoveCount)
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
     * Position-based hints for either participant.
     * Does not mutate the game or authorize a later command.
     */
    @Transactional(readOnly = true)
    public GameLegalMovesResponse getLegalMoves(Long gameId, Long requestingPlayerId) {
        var game = loadGameForParticipant(gameId, requestingPlayerId);

        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            return new GameLegalMovesResponse(game.getId(), game.getVersion(), List.of());
        }

        var position = fenParser.parse(game.getCurrentFen());
        var moves = chessRules.legalMoves(position).stream()
                .map(move -> LegalMoveResponse.fromLegalMove(
                        position,
                        move
                ))
                .sorted(
                        Comparator.comparing(LegalMoveResponse::from)
                                .thenComparing(LegalMoveResponse::to)
                                .thenComparing(move ->
                                        move.promotion() == null
                                                ? ""
                                                : move.promotion().name()
                                )
                )
                .toList();

        return new GameLegalMovesResponse(
                game.getId(),
                game.getVersion(),
                moves
        );
    }

    /**
     * Lists finished games from the subject Player's perspective.
     * The caller must authorize which subject may be requested.
     * Currently, the HTTP controller supplies only the authenticated ID.
     */
    @Transactional(readOnly = true)
    public PlayedGamesResponse getPlayedGames(long subjectPlayerId, int page, int size) {
        if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid played-games pagination");
        }
        var slice = gameRepository.findFinishedForPlayer(subjectPlayerId, PageRequest.of(page, size));
        var games = slice.getContent().stream()
                .map(game -> PlayedGameResponse.from(
                        game,
                        subjectPlayerId
                ))
                .toList();
        return new PlayedGamesResponse(
                games,
                page,
                size,
                slice.hasNext()
        );
    }

    /**
     * Internal lookup used by matchmaking and other application services.
     * This method does not expose another Player's game over HTTP.
     */
    @Transactional(readOnly = true)
    public Optional<Long> findActiveGameId(Long playerId) {
        return activeGamePlayerRepository.findGameIdByPlayerId(playerId);
    }

    private Game loadGameForParticipant(Long gameId, Long requestingPlayerId) {
        var game = gameRepository.findById(gameId)
                .orElseThrow(GameNotFoundException::new);

        boolean isParticipant =
                game.getWhitePlayerId().equals(requestingPlayerId)
                        || game.getBlackPlayerId()
                        .equals(requestingPlayerId);
        // Preserve existence-before-membership error precedence.
        // Authorization happens before reading history or evaluating hints.
        if (!isParticipant) {
            throw new GameAccessDeniedException();
        }
        return game;
    }
}