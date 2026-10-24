package com.chessapp.game.rules;

import com.chessapp.game.rules.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/24/26
 */
class FenParserTest {

    private static final String INITIAL_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                    + " w KQkq - 0 1";

    private final FenParser codec = new FenParser();

    @Test
    void parsesInitialPosition() {
        var position = codec.parse(INITIAL_FEN);

        assertAll(
                () -> assertEquals(
                        new Piece(PieceColor.BLACK, PieceType.ROOK),
                        position.board()
                                .pieceAt(Square.fromAlgebraic("a8"))
                                .orElseThrow()
                ),
                () -> assertEquals(
                        new Piece(PieceColor.WHITE, PieceType.KING),
                        position.board()
                                .pieceAt(Square.fromAlgebraic("e1"))
                                .orElseThrow()
                ),
                () -> assertTrue(
                        position.board()
                                .pieceAt(Square.fromAlgebraic("e4"))
                                .isEmpty()
                ),
                () -> assertEquals(
                        PieceColor.WHITE,
                        position.sideToMove()
                ),
                () -> assertEquals(
                        new CastlingRights(true, true, true, true),
                        position.castlingRights()
                ),
                () -> assertTrue(position.enPassantTarget().isEmpty()),
                () -> assertEquals(0, position.halfmoveClock()),
                () -> assertEquals(1, position.fullmoveNumber())
        );
    }

    @Test
    void parsesMetadataAfterWhiteDoublePawnMove() {
        var position = codec.parse(
                "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
                        + " b KQkq e3 0 1"
        );

        assertAll(
                () -> assertEquals(
                        PieceColor.BLACK,
                        position.sideToMove()
                ),
                () -> assertEquals(
                        Square.fromAlgebraic("e3"),
                        position.enPassantTarget().orElseThrow()
                ),
                () -> assertEquals(
                        new Piece(PieceColor.WHITE, PieceType.PAWN),
                        position.board()
                                .pieceAt(Square.fromAlgebraic("e4"))
                                .orElseThrow()
                )
        );
    }

    @Test
    void parsesAbsentCastlingRightsAndNonzeroCounters() {
        var position = codec.parse(
                "4k3/8/8/8/8/8/8/4K3 b - - 17 42"
        );

        assertAll(
                () -> assertEquals(
                        new CastlingRights(false, false, false, false),
                        position.castlingRights()
                ),
                () -> assertEquals(17, position.halfmoveClock()),
                () -> assertEquals(42, position.fullmoveNumber())
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "8/8/8/8/8/8/8/8 w - - 0",
            "8/8/8/8/8/8/8/8  w - - 0 1",
            "8/8/8/8/8/8/8 w - - 0 1",
            "9/8/8/8/8/8/8/8 w - - 0 1",
            "7/8/8/8/8/8/8/8 w - - 0 1",
            "8P/8/8/8/8/8/8/8 w - - 0 1",
            "44/8/8/8/8/8/8/8 w - - 0 1",
            "7X/8/8/8/8/8/8/8 w - - 0 1",
            "8/8/8/8/8/8/8/8 x - - 0 1",
            "8/8/8/8/8/8/8/8 w KK - 0 1",
            "8/8/8/8/8/8/8/8 w QK - 0 1",
            "8/8/8/8/8/8/8/8 w - e4 0 1",
            "8/8/8/8/8/8/8/8 w - - -1 1",
            "8/8/8/8/8/8/8/8 w - - 0 0",
            "8/8/8/8/8/8/8/8 w - - 0 2147483648"
    })
    void rejectsMalformedFen(String fen) {
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.parse(fen)
        );
    }
}