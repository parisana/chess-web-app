package com.chessapp.game.dto;

import com.chessapp.game.entity.GameStatus;

import java.time.Instant;

/**
 * @author pari on 10/22/26
 */
public record GameResponse(
        Long id,
        Long whitePlayerId,
        Long blackPlayerId,
        GameStatus status,
        String currentFen,
        Instant createdAt
) {
}
