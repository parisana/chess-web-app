package com.chessapp.game.rules.model;

/**
 * @author pari on 10/24/26
 */
public record Square(int file, int rank) {

    public Square {
        if (file < 0 || file > 7 || rank < 0 || rank > 7) {
            throw new IllegalArgumentException(
                    "Square file and rank must be between 0 and 7"
            );
        }
    }

    public static Square fromAlgebraic(String value) {
        if (value == null || value.length() != 2) {
            throw new IllegalArgumentException(
                    "Square must contain a file and rank, for example e4"
            );
        }

        char file = value.charAt(0);
        char rank = value.charAt(1);

        if (file < 'a' || file > 'h' || rank < '1' || rank > '8') {
            throw new IllegalArgumentException(
                    "Square must be between a1 and h8"
            );
        }

        return new Square(file - 'a', rank - '1');
    }

    public String toAlgebraic() {
        return new String(new char[] {
                (char) ('a' + file),
                (char) ('1' + rank)
        });
    }
}
