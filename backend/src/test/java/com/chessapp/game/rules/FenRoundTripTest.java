package com.chessapp.game.rules;

import com.chessapp.game.rules.model.Square;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author pari on 10/24/26
 */
class FenRoundTripTest {

    private static final String INITIAL_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                    + " w KQkq - 0 1";

    private final FenSerializer serializer = new FenSerializer();
    private final FenParser parser = new FenParser();

    @ParameterizedTest
    @ValueSource(strings = {
            INITIAL_FEN,
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
            "4k3/8/8/8/8/8/8/4K3 b - - 17 42",
            "r3k3/8/8/8/8/8/8/4K2R w Kq - 7 12"
    })
    void roundTripsCanonicalFen(String fen) {
        assertEquals(fen, serializer.serialize(parser.parse(fen)));
    }

    @Test
    void serializationPreservesPositionContents() {
        var original = parser.parse(
                "rnbqkbnr/pppp1ppp/8/4p3/8/8/PPPPPPPP/RNBQKBNR"
                        + " w KQkq e6 0 2"
        );

        var restored = parser.parse(serializer.serialize(original));

        assertAll(
                () -> assertEquals(
                        original.sideToMove(),
                        restored.sideToMove()
                ),
                () -> assertEquals(
                        original.castlingRights(),
                        restored.castlingRights()
                ),
                () -> assertEquals(
                        original.enPassantTarget(),
                        restored.enPassantTarget()
                ),
                () -> assertEquals(
                        original.halfmoveClock(),
                        restored.halfmoveClock()
                ),
                () -> assertEquals(
                        original.fullmoveNumber(),
                        restored.fullmoveNumber()
                )
        );

        // Compare occupancy on every square because Board currently
        // uses object identity rather than value-based equality.
        for (int file = 0; file < 8; file++) {
            for (int rank = 0; rank < 8; rank++) {
                var square = new Square(file, rank);

                assertEquals(
                        original.board().pieceAt(square),
                        restored.board().pieceAt(square),
                        "Piece placement differs at " + square.toAlgebraic()
                );
            }
        }
    }
}