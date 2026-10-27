package com.chessapp.game.rules.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MoveTest {

    private final Square from = Square.fromAlgebraic("g7");
    private final Square to = Square.fromAlgebraic("g8");

    @Test
    void ordinaryConstructorLeavesPromotionAbsent() {
        var move = new Move(from, to);

        assertEquals(Optional.empty(), move.promotion());
        assertEquals(
                new Move(from, to, Optional.empty()),
                move
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = PieceType.class,
            names = {"QUEEN", "ROOK", "BISHOP", "KNIGHT"}
    )
    void acceptsEachPromotionChoice(PieceType type) {
        var move = new Move(from, to, Optional.of(type));

        assertEquals(Optional.of(type), move.promotion());
    }

    @ParameterizedTest
    @EnumSource(
            value = PieceType.class,
            names = {"PAWN", "KING"}
    )
    void rejectsInvalidReplacementTypes(PieceType type) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Move(from, to, Optional.of(type))
        );
    }

    @Test
    void promotionChoicesRemainDistinctInSets() {
        var ordinary = new Move(from, to);
        var queen = new Move(from, to, Optional.of(PieceType.QUEEN));
        var rook = new Move(from, to, Optional.of(PieceType.ROOK));
        var bishop = new Move(from, to, Optional.of(PieceType.BISHOP));
        var knight = new Move(from, to, Optional.of(PieceType.KNIGHT));

        var moves = Set.of(ordinary, queen, rook, bishop, knight);

        assertEquals(5, moves.size());
        assertTrue(moves.contains(
                new Move(from, to, Optional.of(PieceType.KNIGHT))
        ));
    }

    @Test
    void rejectsNullComponents() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new Move(null, to)
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new Move(from, null)
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new Move(from, to, null)
                )
        );
    }

    @Test
    void rejectsIdenticalSourceAndDestination() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new Move(from, from)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new Move(
                                from,
                                from,
                                Optional.of(PieceType.QUEEN)
                        )
                )
        );
    }
}