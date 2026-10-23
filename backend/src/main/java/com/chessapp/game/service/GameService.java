package com.chessapp.game.service;

import com.chessapp.game.dto.GameResponse;
import com.chessapp.game.entity.Game;
import com.chessapp.game.exception.GameAccessDeniedException;
import com.chessapp.game.exception.GameNotFoundException;
import com.chessapp.game.repository.GameRepository;
import com.chessapp.player.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chessapp.game.exception.PlayerAlreadyInGameException;
import com.chessapp.game.repository.ActiveGamePlayerRepository;

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
        var game = new Game(whitePlayerId, blackPlayerId);

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
