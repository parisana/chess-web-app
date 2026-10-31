package com.chessapp.game.dto;

import java.util.List;

/**
 * @author pari on 10/30/26
 */
public record GameHistoryResponse(
        Long gameId,
        long version, // from game.version
        String historyStartFen,
        boolean historyComplete, // almost always true
        long recordedMoveCount,
        List<GameMoveResponse> moves // sorted list by sequenceNumber
) {
    public GameHistoryResponse {
        // Prevent callers from changing the response's move collection.
        moves = List.copyOf(moves);
    }
}
