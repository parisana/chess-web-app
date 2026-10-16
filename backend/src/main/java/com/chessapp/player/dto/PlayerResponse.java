package com.chessapp.player.dto;

/**
 * @author pari on 10/16/26
 */
public record PlayerResponse(
        Long id,
        String username,
        String email
) {
}
