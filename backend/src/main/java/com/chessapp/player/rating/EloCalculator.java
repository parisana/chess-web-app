package com.chessapp.player.rating;

import java.util.Objects;

/**
 * @author pari on 11/1/26
 * Pure Elo calculation using a fixed K-factor.
 * No Spring, persistence or game-feature dependencies.
 */
public final class EloCalculator {

    public static final int INITIAL_RATING = 1200;

    private static final int K_FACTOR = 32;
    private static final double RATING_SCALE = 400.0;

    private EloCalculator() {
    }

    /**
     * Calculates both ratings from their pre-game values.

     * Expected score increases as the first Player's rating exceeds
     * the opponent's rating. A win against a stronger opponent therefore
     * earns more points than a win against a weaker opponent.

     * Round one adjustment and apply its opposite to the opponent,
     * preserving the pair's total rating exactly.
     */
    public static RatingChange calculate(
            int firstPlayerRating,
            int secondPlayerRating,
            RatingOutcome firstPlayerOutcome
    ) {
        Objects.requireNonNull(
                firstPlayerOutcome,
                "First Player outcome is required"
        );

        // Convert before subtraction to avoid integer subtraction overflow.
        double ratingDifference =
                (double) secondPlayerRating - firstPlayerRating;

        double expectedScore = 1.0 / (
                1.0 + Math.pow(10.0, ratingDifference / RATING_SCALE)
        );

        int delta = (int) Math.round(
                K_FACTOR * (firstPlayerOutcome.score() - expectedScore)
        );

        return new RatingChange(
                Math.addExact(firstPlayerRating, delta),
                Math.subtractExact(secondPlayerRating, delta),
                delta
        );
    }
}
