package com.chessapp.player.rating;

/**
 * @author pari on 11/1/26
 * Both updated ratings and the points gained by the first Player.
 * The second Player receives the opposite adjustment.
 */
public record RatingChange(
        int firstPlayerRating,
        int secondPlayerRating,
        int firstPlayerDelta
) {
}
