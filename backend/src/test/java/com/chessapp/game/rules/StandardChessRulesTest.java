package com.chessapp.game.rules;

import com.chessapp.game.rules.model.Piece;
import com.chessapp.game.rules.model.PieceColor;
import com.chessapp.game.rules.model.PieceType;
import com.chessapp.game.rules.model.Square;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/24/26
 */
class StandardChessRulesTest {

    private final ChessRules rules =
            new StandardChessRules(new FenParser());

    @Test
    void initialPositionContainsEveryStartingPiece() {
        var position = rules.initialPosition();

        PieceType[] backRank = {
                PieceType.ROOK,
                PieceType.KNIGHT,
                PieceType.BISHOP,
                PieceType.QUEEN,
                PieceType.KING,
                PieceType.BISHOP,
                PieceType.KNIGHT,
                PieceType.ROOK
        };

        for (int file = 0; file < 8; file++) {
            assertEquals(
                    new Piece(PieceColor.WHITE, backRank[file]),
                    position.board().pieceAt(new Square(file, 0))
                            .orElseThrow()
            );
            assertEquals(
                    new Piece(PieceColor.BLACK, backRank[file]),
                    position.board().pieceAt(new Square(file, 7))
                            .orElseThrow()
            );

            for (int rank : new int[] {1, 6}) {
                var color = rank == 1
                        ? PieceColor.WHITE
                        : PieceColor.BLACK;

                assertEquals(
                        new Piece(color, PieceType.PAWN),
                        position.board().pieceAt(new Square(file, rank))
                                .orElseThrow()
                );
            }

            for (int rank = 2; rank <= 5; rank++) {
                assertTrue(
                        position.board().pieceAt(new Square(file, rank))
                                .isEmpty()
                );
            }
        }
    }
}