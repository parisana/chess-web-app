package com.chessapp.auth.dto;

/**
 * @author pari on 10/16/26
 */
public record RegisterResponse(
        Long playerId,
        String username,
        String email
) {
}
