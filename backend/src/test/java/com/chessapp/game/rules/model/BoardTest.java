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

    @Test
    void movesPieceToEmptySquareWithoutChangingOriginalBoard() {
        var from = Square.fromAlgebraic("d4");
        var to = Square.fromAlgebraic("f5");
        var kingSquare = Square.fromAlgebraic("e1");

        var knight = new Piece(PieceColor.WHITE, PieceType.KNIGHT);
        var king = new Piece(PieceColor.WHITE, PieceType.KING);

        var original = new Board(Map.of(
                from, knight,
                kingSquare, king
        ));

        var candidate = original.movePiece(from, to);

        assertAll(
                () -> assertTrue(candidate.pieceAt(from).isEmpty()),
                () -> assertEquals(
                        knight,
                        candidate.pieceAt(to).orElseThrow()
                ),
                () -> assertEquals(
                        king,
                        candidate.pieceAt(kingSquare).orElseThrow()
                ),
                () -> assertEquals(
                        knight,
                        original.pieceAt(from).orElseThrow()
                ),
                () -> assertTrue(original.pieceAt(to).isEmpty())
        );
    }

    @Test
    void replacesCapturedPieceWithoutChangingOriginalBoard() {
        var from = Square.fromAlgebraic("d4");
        var to = Square.fromAlgebraic("f5");

        var knight = new Piece(PieceColor.WHITE, PieceType.KNIGHT);
        var rook = new Piece(PieceColor.BLACK, PieceType.ROOK);

        var original = new Board(Map.of(
                from, knight,
                to, rook
        ));

        var candidate = original.movePiece(from, to);

        assertAll(
                () -> assertTrue(candidate.pieceAt(from).isEmpty()),
                () -> assertEquals(
                        knight,
                        candidate.pieceAt(to).orElseThrow()
                ),
                () -> assertEquals(
                        knight,
                        original.pieceAt(from).orElseThrow()
                ),
                () -> assertEquals(
                        rook,
                        original.pieceAt(to).orElseThrow()
                )
        );
    }

    @Test
    void rejectsMovingFromEmptySquare() {
        var board = new Board(Map.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> board.movePiece(
                        Square.fromAlgebraic("d4"),
                        Square.fromAlgebraic("f5")
                )
        );
    }

    @Test
    void rejectsMovingToSameSquare() {
        var square = Square.fromAlgebraic("d4");
        var board = new Board(Map.of(
                square,
                new Piece(PieceColor.WHITE, PieceType.KNIGHT)
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> board.movePiece(square, square)
        );
    }

    @Test
    void removesPieceWithoutChangingOriginalBoardOrOtherSquares() {
        var capturedSquare = Square.fromAlgebraic("d5");
        var pawnSquare = Square.fromAlgebraic("e5");
        var whiteKingSquare = Square.fromAlgebraic("a1");
        var blackKingSquare = Square.fromAlgebraic("h8");

        var capturedPawn = new Piece(PieceColor.BLACK, PieceType.PAWN);
        var movingPawn = new Piece(PieceColor.WHITE, PieceType.PAWN);
        var whiteKing = new Piece(PieceColor.WHITE, PieceType.KING);
        var blackKing = new Piece(PieceColor.BLACK, PieceType.KING);

        var original = new Board(Map.of(
                capturedSquare, capturedPawn,
                pawnSquare, movingPawn,
                whiteKingSquare, whiteKing,
                blackKingSquare, blackKing
        ));

        var candidate = original.removePiece(capturedSquare);

        // Compare contents explicitly: Board uses identity equality.
        for (int file = 0; file < 8; file++) {
            for (int rank = 0; rank < 8; rank++) {
                var square = new Square(file, rank);

                if (square.equals(capturedSquare)) {
                    assertTrue(candidate.pieceAt(square).isEmpty());
                } else {
                    assertEquals(
                            original.pieceAt(square),
                            candidate.pieceAt(square),
                            square.toAlgebraic()
                    );
                }
            }
        }

        assertEquals(
                capturedPawn,
                original.pieceAt(capturedSquare).orElseThrow()
        );
    }

    @Test
    void rejectsRemovingFromEmptySquare() {
        var board = new Board(Map.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> board.removePiece(Square.fromAlgebraic("d5"))
        );
    }

    @Test
    void rejectsNullRemovalSquare() {
        var board = new Board(Map.of());

        assertThrows(
                NullPointerException.class,
                () -> board.removePiece(null)
        );
    }
}