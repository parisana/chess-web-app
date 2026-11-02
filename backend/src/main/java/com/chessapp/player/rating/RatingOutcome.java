package com.chessapp.player.rating;

/**
 * @author pari on 11/1/26
 * The outcome from the first Player's perspective.
 * Independent of game entities, piece colors and persistence.
 * Keeping this inside player.rating means the calculator doesn’t need to know about GameResult.
 * Later, the completion listener will translate the game result into this type.
 */
public enum RatingOutcome {
    WIN(1.0),
    DRAW(0.5),
    LOSS(0.0);

    private final double score;

    RatingOutcome(double score) {
        this.score = score;
    }

    public double score() {
        return score;
    }
}