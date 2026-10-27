package com.chessapp.game.rules.model;

import java.util.Objects;
import java.util.Optional;

/**
 * @author pari on 10/24/26
 * Represents a proposed move, without establishing its legality.
 */
public record Move(
        Square from,
        Square to,
        Optional<PieceType> promotion
) {

    // Preserve convenient construction of ordinary moves.
    public Move(Square from, Square to) {
        this(from, to, Optional.empty());
    }

    public Move {
        Objects.requireNonNull(from, "Source square is required");
        Objects.requireNonNull(to, "Destination square is required");
        Objects.requireNonNull(
                promotion,
                "Promotion must use Optional.empty() when absent"
        );

        if (from.equals(to)) {
            throw new IllegalArgumentException(
                    "Source and destination squares must differ"
            );
        }

        // Validate the choice itself; board-dependent rules belong elsewhere.
        promotion.ifPresent(type -> {
            if (type == PieceType.PAWN || type == PieceType.KING) {
                throw new IllegalArgumentException(
                        "Promotion must select queen, rook, bishop or knight"
                );
            }
        });
    }
}
