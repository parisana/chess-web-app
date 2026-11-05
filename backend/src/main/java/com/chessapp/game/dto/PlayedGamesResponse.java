package com.chessapp.game.dto;

import java.util.List;

/**
 * @author pari on 11/5/26
 */
public record PlayedGamesResponse(
        List<PlayedGameResponse> games,
        int page,
        int size,
        boolean hasNext
) {
    public PlayedGamesResponse {
        games = List.copyOf(games);
    }
}
