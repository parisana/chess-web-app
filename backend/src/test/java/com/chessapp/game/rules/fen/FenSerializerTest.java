package com.chessapp.game.rules.fen;

import com.chessapp.game.rules.model.*;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author pari on 10/24/26
 */
class FenSerializerTest {

    private static final String INITIAL_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                    + " w KQkq - 0 1";

    private final FenSerializer codec = new FenSerializer();

    @Test
    void serializesConstructedPosition() {
        var position = new ChessPosition(
                new Board(Map.of(
                        Square.fromAlgebraic("a8"),
                        new Piece(PieceColor.BLACK, PieceType.ROOK),
                        Square.fromAlgebraic("e8"),
                        new Piece(PieceColor.BLACK, PieceType.KING),
                        Square.fromAlgebraic("e1"),
                        new Piece(PieceColor.WHITE, PieceType.KING),
                        Square.fromAlgebraic("h1"),
                        new Piece(PieceColor.WHITE, PieceType.ROOK)
                )),
                PieceColor.WHITE,
                new CastlingRights(true, false, false, true),
                Optional.empty(),
                7,
                12
        );

        assertEquals(
                "r3k3/8/8/8/8/8/8/4K2R w Kq - 7 12",
                codec.serialize(position)
        );
    }
}