package com.chessapp.game.dto;

import com.chessapp.game.entity.GameMove;
import com.chessapp.game.rules.model.PieceType;

import java.time.Instant;

/**
 * @author pari on 10/30/26
 */
public record GameMoveResponse(
        long sequenceNumber,
        Long playerId,
        String from,
        String to,
        PieceType promotion,
        String resultingFen,
        Instant playedAt
) {
    public static GameMoveResponse from(GameMove move) {
        return new GameMoveResponse(
                move.getSequenceNumber(),
                move.getPlayerId(),
                move.getFromSquare(),
                move.getToSquare(),
                move.getPromotion(),
                move.getResultingFen(),
                move.getPlayedAt()
        );
    }
}
