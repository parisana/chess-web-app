package com.chessapp.game.dto;

import java.util.List;

/**
 * @author pari on 11/4/26
 */
public record GameLegalMovesResponse(
        Long gameId,
        long version,
        List<LegalMoveResponse> moves
) {
    public GameLegalMovesResponse {
        moves = List.copyOf(moves);
    }
}
