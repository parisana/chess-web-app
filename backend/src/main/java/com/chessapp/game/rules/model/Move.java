package com.chessapp.game.rules.model;

import java.util.Objects;

/**
 * @author pari on 10/24/26
 * Represents a proposed move, without establishing its legality.
 */
public record Move(Square from, Square to) {

    public Move {
        Objects.requireNonNull(from, "Source square is required");
        Objects.requireNonNull(to, "Destination square is required");

        if (from.equals(to)) {
            throw new IllegalArgumentException(
                    "Source and destination squares must differ"
            );
        }
    }
}
