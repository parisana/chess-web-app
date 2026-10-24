package com.chessapp.game.rules.model;

import java.util.Objects;

public record Piece(PieceColor color, PieceType type) {

    public Piece {
        Objects.requireNonNull(color, "Piece color is required");
        Objects.requireNonNull(type, "Piece type is required");
    }
}