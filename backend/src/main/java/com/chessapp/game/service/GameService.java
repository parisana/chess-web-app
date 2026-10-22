package com.chessapp.game.service;

import com.chessapp.game.dto.GameResponse;
import com.chessapp.game.entity.Game;
import com.chessapp.game.repository.GameRepository;
import com.chessapp.player.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author pari on 10/22/26
 */
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;
    private final PlayerService playerService;

    @Transactional
    public GameResponse createGame(
            Long whitePlayerId,
            Long blackPlayerId
    ) {
        // Constructing the entity first rejects invalid or identical IDs before database lookups.
        var game = new Game(whitePlayerId, blackPlayerId);

        // Player existence goes through PlayerService; foreign keys remain the final database guarantee.
        requirePlayer(whitePlayerId);
        requirePlayer(blackPlayerId);

        return toResponse(gameRepository.save(game));
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
