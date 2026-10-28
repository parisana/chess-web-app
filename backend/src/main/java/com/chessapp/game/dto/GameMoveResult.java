package com.chessapp.game.dto;

import com.chessapp.game.entity.Game;
import com.chessapp.game.entity.GameResult;
import com.chessapp.game.entity.GameStatus;
import com.chessapp.game.entity.GameTerminationReason;
import com.chessapp.game.rules.evaluation.PositionStatus;

import java.time.Instant;

/**
 * @author pari on 10/28/26
 */
public record GameMoveResult(Long gameId,
                             String currentFen,
                             Long version,
                             PositionStatus positionStatus,
                             GameStatus status,
                             GameResult result,
                             GameTerminationReason terminationReason,
                             Instant finishedAt
) {

    public static GameMoveResult from(
            Game game,
            PositionStatus assessment
    ) {
        return new GameMoveResult(
                game.getId(),
                game.getCurrentFen(),
                game.getVersion(),
                assessment,
                game.getStatus(),
                game.getResult(),
                game.getTerminationReason(),
                game.getFinishedAt()
        );
    }
}
