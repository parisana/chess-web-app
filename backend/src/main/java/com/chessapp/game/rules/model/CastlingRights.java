package com.chessapp.game.rules.model;

/**
 * @author pari on 10/24/26
 */
public record CastlingRights(
        boolean whiteKingSide,
        boolean whiteQueenSide,
        boolean blackKingSide,
        boolean blackQueenSide
) {
}
