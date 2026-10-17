package com.chessapp.auth.dto;

/**
 * @author pari on 10/16/26
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
