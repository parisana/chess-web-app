package com.chessapp.game.rules.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/24/26
 */
class SquareTest {

    @ParameterizedTest
    @CsvSource({
            "a1, 0, 0",
            "h1, 7, 0",
            "a8, 0, 7",
            "h8, 7, 7",
            "e4, 4, 3"
    })
    void convertsBetweenNotationAndCoordinates(
            String notation,
            int file,
            int rank
    ) {
        var square = Square.fromAlgebraic(notation);

        assertAll(
                () -> assertEquals(file, square.file()),
                () -> assertEquals(rank, square.rank()),
                () -> assertEquals(notation, square.toAlgebraic())
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "a0", "a9", "i1", "A1", "e", "e44", " e4", "e4 "
    })
    void rejectsInvalidNotation(String notation) {
        assertThrows(
                IllegalArgumentException.class,
                () -> Square.fromAlgebraic(notation)
        );
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 0",
            "8, 0",
            "0, -1",
            "0, 8"
    })
    void rejectsCoordinatesOutsideBoard(int file, int rank) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Square(file, rank)
        );
    }

    @Test
    void roundTripsEveryBoardSquare() {
        // Check all 64 squares: converting to algebraic notation and back
        // must preserve the original file and rank.
        for (int file = 0; file < 8; file++) {
            for (int rank = 0; rank < 8; rank++) {
                var square = new Square(file, rank);

                assertEquals(
                        square,
                        Square.fromAlgebraic(square.toAlgebraic())
                );
            }
        }
    }
}