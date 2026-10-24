package com.chessapp.game.rules.model;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.HashMap;

/**
 * @author pari on 10/24/26
 */
public final class Board {

    private final Map<Square, Piece> pieces;

    public Board(Map<Square, Piece> pieces) {
        Objects.requireNonNull(pieces, "Board pieces are required");

        // Copy the caller's entries into an unmodifiable map so later
        // changes to the input cannot change this board.
        this.pieces = Map.copyOf(pieces);
    }

    public Optional<Piece> pieceAt(Square square) {
        Objects.requireNonNull(square, "Square is required");

        return Optional.ofNullable(pieces.get(square));
    }

    /**
     * Returns a new board with the source piece relocated.
     * This changes placement only; it does not validate chess movement rules.
     */
    public Board movePiece(Square from, Square to) {
        Objects.requireNonNull(from, "Source square is required");
        Objects.requireNonNull(to, "Destination square is required");

        if (from.equals(to)) {
            throw new IllegalArgumentException(
                    "Source and destination squares must differ"
            );
        }

        var movingPiece = pieceAt(from)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Source square must contain a piece"
                ));

        var updatedPieces = new HashMap<>(pieces);
        updatedPieces.remove(from);

        // Replacing an existing destination entry represents a capture.
        updatedPieces.put(to, movingPiece);

        return new Board(updatedPieces);
    }
}
