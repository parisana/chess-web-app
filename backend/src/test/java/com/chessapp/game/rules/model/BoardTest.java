package com.chessapp.game.rules.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/24/26
 */
class BoardTest {

    @Test
    void returnsPieceOnOccupiedSquare() {
        var square = Square.fromAlgebraic("e1");
        var king = new Piece(PieceColor.WHITE, PieceType.KING);

        var board = new Board(Map.of(square, king));

        assertEquals(king, board.pieceAt(square).orElseThrow());
    }

    @Test
    void returnsEmptyForUnoccupiedSquare() {
        var board = new Board(Map.of(
                Square.fromAlgebraic("e1"),
                new Piece(PieceColor.WHITE, PieceType.KING)
        ));

        assertTrue(
                board.pieceAt(Square.fromAlgebraic("e2")).isEmpty()
        );
    }

    @Test
    void inputMapChangesDoNotChangeBoard() {
        var square = Square.fromAlgebraic("e1");
        var king = new Piece(PieceColor.WHITE, PieceType.KING);
        var replacement = new Piece(PieceColor.BLACK, PieceType.QUEEN);

        var input = new HashMap<Square, Piece>();
        input.put(square, king);

        var board = new Board(input);

        // The caller still owns this mutable map. Changing it must not
        // alter a board that has already been constructed.
        input.put(square, replacement);
        input.put(
                Square.fromAlgebraic("a1"),
                new Piece(PieceColor.WHITE, PieceType.ROOK)
        );

        assertAll(
                () -> assertEquals(
                        king,
                        board.pieceAt(square).orElseThrow()
                ),
                () -> assertTrue(
                        board.pieceAt(
                                Square.fromAlgebraic("a1")
                        ).isEmpty()
                )
        );
    }

    @Test
    void supportsAnEmptyBoard() {
        var board = new Board(Map.of());

        for (int file = 0; file < 8; file++) {
            for (int rank = 0; rank < 8; rank++) {
                assertTrue(
                        board.pieceAt(new Square(file, rank)).isEmpty()
                );
            }
        }
    }
}