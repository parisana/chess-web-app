package com.chessapp.game.service;

import com.chessapp.game.dto.GameHistoryResponse;
import com.chessapp.game.dto.GameMoveResponse;
import com.chessapp.game.dto.GameResponse;
import com.chessapp.game.exception.GameAccessDeniedException;
import com.chessapp.game.exception.GameNotFoundException;
import com.chessapp.game.export.PgnFormatter;
import com.chessapp.game.repository.GameMoveRepository;
import com.chessapp.game.repository.GameRepository;
import com.chessapp.player.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/**
 * @author pari on 10/31/26
 */
@Service
@RequiredArgsConstructor
public class GameExportService {

    private final GameRepository gameRepository;
    private final GameMoveRepository gameMoveRepository;
    private final PlayerService playerService;
    private final PgnFormatter pgnFormatter;

    @Transactional(readOnly = true)
    public String exportPgn(Long gameId, Long requestingPlayerId) {
        var game = gameRepository.findById(gameId)
                .orElseThrow(GameNotFoundException::new);

        boolean isParticipant =
                game.getWhitePlayerId().equals(requestingPlayerId)
                        || game.getBlackPlayerId()
                        .equals(requestingPlayerId);

        // Authorize before exposing history or export availability.
        if (!isParticipant) {
            throw new GameAccessDeniedException();
        }

        // Capture result/version and the matching move boundary together.
        var snapshot = GameResponse.from(game);
        long recordedMoveCount = game.getRecordedMoveCount();

        var moves = gameMoveRepository.findHistoryThrough(
                        game.getId(),
                        recordedMoveCount
                )
                .stream()
                .map(GameMoveResponse::from)
                .toList();

        var history = new GameHistoryResponse(
                snapshot.id(),
                snapshot.version(),
                game.getHistoryStartFen(),
                game.isHistoryComplete(),
                recordedMoveCount,
                moves
        );

        return pgnFormatter.format(
                snapshot,
                history,
                username(snapshot.whitePlayerId()),
                username(snapshot.blackPlayerId())
        );
    }

    private String username(Long playerId) {
        // Access the Player feature through its service.
        return playerService.findById(playerId)
                .orElseThrow(() -> new IllegalStateException(
                        "Game participant does not exist: " + playerId
                ))
                .username();
    }
}
