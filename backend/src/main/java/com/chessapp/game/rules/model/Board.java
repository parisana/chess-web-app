package com.chessapp.game.rules.model;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
}
