package com.chessapp.player.dto;

/**
 * @author pari on 11/4/26
 * Current Player details needed when creating a game.
 * Excludes private profile fields such as email.
 */
public record PlayerGameDetails(
        String username,
        int rating
) {
}
