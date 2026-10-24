package com.chessapp.game.rules.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/24/26
 */
class ChessPositionTest {

    @Test
    void representsPositionWithoutEnPassantTarget() {
        var position = position(Optional.empty(), 0, 1);

        assertAll(
                () -> assertEquals(
                        PieceColor.WHITE,
                        position.sideToMove()
                ),
                () -> assertTrue(position.enPassantTarget().isEmpty()),
                () -> assertEquals(0, position.halfmoveClock()),
                () -> assertEquals(1, position.fullmoveNumber())
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"e3", "d6"})
    void acceptsEnPassantTargetOnThirdOrSixthRank(String notation) {
        var target = Square.fromAlgebraic(notation);

        var position = position(Optional.of(target), 0, 1);

        assertEquals(Optional.of(target), position.enPassantTarget());
    }

    @ParameterizedTest
    @ValueSource(strings = {"a1", "b2", "c4", "d5", "e7", "f8"})
    void rejectsEnPassantTargetOnOtherRanks(String notation) {
        var target = Square.fromAlgebraic(notation);

        assertThrows(
                IllegalArgumentException.class,
                () -> position(Optional.of(target), 0, 1)
        );
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 1",
            "0, 0",
            "0, -1"
    })
    void rejectsInvalidCounters(int halfmoveClock, int fullmoveNumber) {
        assertThrows(
                IllegalArgumentException.class,
                () -> position(
                        Optional.empty(),
                        halfmoveClock,
                        fullmoveNumber
                )
        );
    }

    @Test
    void preservesIndependentCastlingRights() {
        var rights = new CastlingRights(true, false, false, true);

        var position = new ChessPosition(
                new Board(Map.of()),
                PieceColor.BLACK,
                rights,
                Optional.empty(),
                12,
                20
        );

        assertAll(
                () -> assertTrue(
                        position.castlingRights().whiteKingSide()
                ),
                () -> assertFalse(
                        position.castlingRights().whiteQueenSide()
                ),
                () -> assertFalse(
                        position.castlingRights().blackKingSide()
                ),
                () -> assertTrue(
                        position.castlingRights().blackQueenSide()
                )
        );
    }

    private static ChessPosition position(
            Optional<Square> enPassantTarget,
            int halfmoveClock,
            int fullmoveNumber
    ) {
        // Small representation fixtures do not need to be playable positions.
        return new ChessPosition(
                new Board(Map.of()),
                PieceColor.WHITE,
                new CastlingRights(false, false, false, false),
                enPassantTarget,
                halfmoveClock,
                fullmoveNumber
        );
    }
}